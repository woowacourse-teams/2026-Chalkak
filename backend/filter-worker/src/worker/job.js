// 메시지 한 건의 처리 순서(README 설계 3-1절). 결과는 SQS 루프가 쓰는 세 가지 중 하나다.
//   'delete'  메시지를 지운다
//   'retry'   지우지 않아 숨김 시간이 끝난 뒤 SQS가 다시 주게 한다
//   'release' 종료 신호로 중단했다. 숨김 0초로 반납해 바로 다시 받게 한다
//
// 숨김 시간 연장(heartbeat)은 SQS 루프의 일이다. handleMessage는 끝날 때까지 기다리는 하나의 Promise이므로
// 루프가 그 Promise가 끝날 때까지 타이머로 ChangeMessageVisibility를 부르면 된다. 이 파일은 SQS를 모른다.
//
// 주입 값(deps):
//   config  { bucket, prefix, environments, workDir, maxReceiveCount = 5, maxBytes = DEFAULT_MAX_BYTES, extractionTimeoutMs }
//   s3      send(command)만 있는 클라이언트
//   callbacks  createCallbackClient()의 결과(start·complete·failed)
//   runExtraction  extract-runner의 runExtraction과 같은 시그니처
//   log     { info(event, fields), warn(event, fields), error(event, fields) } (시크릿·서명·전체 URL은 넘기지 않는다)
//   now     () => 밀리초 (기본 Date.now)
import { mkdtemp, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { INVALID_PROBE, PROCESSING_FAILED, PROCESSING_FAILED_MESSAGE } from '../failure.js';
import { DEFAULT_MAX_BYTES, TOO_LARGE_MESSAGE } from '../probe-input.js';
import { CallbackError, outcomeForCallbackError } from './callback-client.js';
import { downloadProbe, putRecipe } from './probe-store.js';
import { parseProbeEvent } from './s3-event.js';

export const DEFAULT_MAX_RECEIVE_COUNT = 5;

const describeError = (error) => `${error?.name ?? 'Error'}: ${error?.message ?? String(error)}`;

export function createJobHandler({ config, s3, callbacks, runExtraction, log, now = Date.now }) {
  const { bucket, prefix, environments, workDir, extractionTimeoutMs } = config;
  const maxReceiveCount = config.maxReceiveCount ?? DEFAULT_MAX_RECEIVE_COUNT;
  const maxBytes = config.maxBytes ?? DEFAULT_MAX_BYTES;

  return async function handleMessage(body, { messageId, receiveCount, signal }) {
    const event = parseProbeEvent(body, { bucket, prefix, environments });
    if (event.kind === 'ignore') {
      // 믿을 수 있는 probeId가 없으므로 콜백 없이 지운다
      const fields = { messageId, reason: event.reason, ...(event.key === undefined ? {} : { key: event.key }) };
      (event.reason === 'test_event' ? log.info : log.warn)('message_ignored', fields);
      return 'delete';
    }

    const { env, probeId, key, size } = event;
    const fields = { messageId, receiveCount, env, probeId };
    const startedAt = now();
    let stage = 'received';

    // 실패 콜백을 보내고 메시지를 어떻게 할지 돌려준다(1-5절 failed 열)
    async function reportFailure(failureType, message, reason) {
      let callback = 'ok';
      let outcome = 'delete';
      try {
        await callbacks.failed(env, probeId, { type: failureType, message }, { signal });
      } catch (error) {
        callback = error instanceof CallbackError ? error.kind : 'error';
        outcome = error instanceof CallbackError ? outcomeForCallbackError('failed', error) : 'retry';
        if (outcome === 'delete') {
          log.error('callback_permanently_refused', { ...fields, action: 'failed', status: error.status });
        }
      }
      log.info('failure_reported', { ...fields, failureType, reason, callback });
      return outcome;
    }

    async function abandon() {
      stage = 'abandon';
      const outcome = await reportFailure(PROCESSING_FAILED, PROCESSING_FAILED_MESSAGE, 'abandoned');
      log.error('job_abandoned', { ...fields, failureType: PROCESSING_FAILED, outcome });
      // 수신 횟수를 다 썼으므로 failed 결과와 상관없이 지운다. 종료 신호로 보내지 못했을 때만 반납한다
      return outcome === 'release' ? 'release' : 'delete';
    }

    async function run(jobDir) {
      stage = 'start';
      let recipeId;
      try {
        const started = await callbacks.start(env, probeId);
        if (!started.proceed) {
          log.info('start_skipped', fields);
          return 'delete';
        }
        recipeId = started.recipeId;
      } catch (error) {
        return error instanceof CallbackError ? outcomeForCallbackError('start', error) : 'retry';
      }

      if (size !== null && size > maxBytes) {
        stage = 'size-check';
        return reportFailure(INVALID_PROBE, TOO_LARGE_MESSAGE, 'too_large');
      }

      stage = 'download';
      const probePath = join(jobDir, 'probe.jpg');
      const downloaded = await downloadProbe(s3, { bucket, key, maxBytes, dest: probePath, signal });
      if (downloaded.kind === 'too-large') return reportFailure(INVALID_PROBE, TOO_LARGE_MESSAGE, 'too_large');
      if (downloaded.kind === 'missing') return reportFailure(PROCESSING_FAILED, PROCESSING_FAILED_MESSAGE, 'missing');

      stage = 'extract';
      const result = await runExtraction({ probePath, outDir: jobDir, timeoutMs: extractionTimeoutMs, signal });
      switch (result.kind) {
        case 'ok':
          break;
        case 'invalid':
          log.info('extraction_rejected', { ...fields, failureType: INVALID_PROBE, message: result.message });
          return reportFailure(INVALID_PROBE, result.message, 'invalid_probe');
        case 'timeout':
          log.error('extraction_failed', { ...fields, kind: 'timeout', durationMs: result.durationMs });
          return reportFailure(PROCESSING_FAILED, PROCESSING_FAILED_MESSAGE, 'timeout');
        case 'aborted':
          return 'release';
        default: // error, crash
          log.error('extraction_failed', {
            ...fields, kind: result.kind, exitCode: result.exitCode, signal: result.signal, cause: result.cause, durationMs: result.durationMs,
          });
          return 'retry';
      }
      const { meta } = result;
      log.info('extraction_finished', {
        ...fields, branch: meta.branch, holdout: meta.holdout, warnings: meta.warnings?.length ?? 0, durationMs: result.durationMs,
      });

      stage = 'put-recipe';
      const recipeKey = `${prefix}/filters/${env}/recipes/${recipeId}.json`;
      await putRecipe(s3, { bucket, key: recipeKey, body: result.recipeBytes, signal });
      log.info('recipe_written', { ...fields, key: recipeKey, bytes: result.recipeBytes.length });

      stage = 'complete';
      const holdout = meta.holdout?.[meta.branch];
      // 본문 키 순서는 README 1-3절과 같다
      const completeBody = {
        extractorVersion: meta.extractorVersion,
        probeVersion: meta.probeVersion,
        branch: meta.branch,
        holdoutDeltaE: Number.isFinite(holdout) ? holdout : null,
        warningMessages: meta.warnings,
        xmpSummary: meta.xmpSummary,
      };
      try {
        await callbacks.complete(env, probeId, completeBody, { signal });
        return 'delete';
      } catch (error) {
        if (!(error instanceof CallbackError)) return 'retry';
        const outcome = outcomeForCallbackError('complete', error);
        if (outcome !== 'close') return outcome;
        // complete가 영구 거부됐다. 견본이 PROCESSING에 머물지 않게 failed로 닫는다
        log.error('callback_permanently_refused', { ...fields, action: 'complete', status: error.status });
        stage = 'close';
        return reportFailure(PROCESSING_FAILED, PROCESSING_FAILED_MESSAGE, 'complete_refused');
      }
    }

    let outcome;
    let jobDir;
    try {
      if (receiveCount >= maxReceiveCount) {
        outcome = await abandon();
      } else {
        jobDir = await mkdtemp(join(workDir, 'job-'));
        outcome = await run(jobDir);
      }
    } catch (error) {
      // 예상 밖 오류(S3 접근 거부, 네트워크, 중단 등). 종료 신호 때문이면 반납하고 아니면 남긴다
      outcome = signal?.aborted ? 'release' : 'retry';
      log.warn('job_step_failed', { ...fields, stage, error: describeError(error) });
    } finally {
      if (jobDir) await rm(jobDir, { recursive: true, force: true }).catch(() => {});
    }
    log.info('job_finished', { ...fields, outcome, stage, durationMs: now() - startedAt });
    return outcome;
  };
}
