// 메시지 처리 단계: 3-1절 각 갈래와 1-5절 complete·failed 열. 가짜 콜백·S3·실행기만 쓰고 네트워크는 쓰지 않는다.
import { afterEach, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import { GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { DEFAULT_MAX_BYTES, TOO_LARGE_MESSAGE } from '../src/probe-input.js';
import { PROCESSING_FAILED_MESSAGE } from '../src/failure.js';
import { CallbackError } from '../src/worker/callback-client.js';
import { createJobHandler } from '../src/worker/job.js';

const PROBE_ID = '0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f';
const RECIPE_ID = '0190f5c2-aaaa-7c3e-9d2f-1a2b3c4d5e6f';
const BUCKET = 'techcourse-project-2026';
const PREFIX = 'chalkak';
const KEY = `chalkak/filter-probes/dev/${PROBE_ID}.jpg`;

const eventBody = ({ key = KEY, size = 1000, env } = {}) =>
  JSON.stringify({
    Records: [{ eventSource: 'aws:s3', eventName: 'ObjectCreated:Put', s3: { bucket: { name: BUCKET }, object: { key: env ? key.replace('/dev/', `/${env}/`) : key, size } } }],
  });

const META = {
  branch: 'A2',
  holdout: { M1: 0.9, A2: 0.5, G1: 0.7 },
  warnings: ['경고 문구'],
  xmpSummary: { Clarity2012: 10, masks: false },
  extractorVersion: '1.0.0',
  probeVersion: 2,
};
const OK_RESULT = { kind: 'ok', recipeBytes: Buffer.from('{"schemaVersion":1}'), meta: META, durationMs: 1234 };

const callbackError = (kind, status = null) => new CallbackError(kind, `${kind} ${status}`, { status });
const awsError = (name) => Object.assign(new Error(name), { name });

describe('견본 메시지 처리', () => {
  let workDir, calls, logs;

  beforeEach(async () => {
    workDir = await mkdtemp(join(tmpdir(), 'job-test-'));
    calls = [];
    logs = [];
  });
  afterEach(() => rm(workDir, { recursive: true, force: true }));

  // 각 의존성의 동작을 바꿀 수 있는 하네스. 호출 순서는 calls에 쌓인다
  function harness(overrides = {}) {
    const behavior = {
      start: async () => ({ proceed: true, recipeId: RECIPE_ID }),
      complete: async () => ({ status: 204 }),
      failed: async () => ({ status: 204 }),
      get: () => ({ Body: Readable.from([Buffer.from('jpeg')]), ContentLength: 4 }),
      put: () => ({}),
      run: async () => OK_RESULT,
      ...overrides,
    };
    const callbacks = {
      start: async (...args) => (calls.push(['start', ...args]), behavior.start(...args)),
      complete: async (env, id, body, options) => (calls.push(['complete', env, id, body]), behavior.complete(env, id, body, options)),
      failed: async (env, id, body, options) => (calls.push(['failed', env, id, body]), behavior.failed(env, id, body, options)),
    };
    const s3 = {
      async send(command) {
        if (command instanceof GetObjectCommand) {
          calls.push(['get', command.input.Key]);
          const result = behavior.get();
          if (result instanceof Error) throw result;
          return result;
        }
        assert.ok(command instanceof PutObjectCommand);
        calls.push(['put', command.input.Key, command.input.ContentType, command.input.CacheControl, command.input.Body]);
        const result = behavior.put();
        if (result instanceof Error) throw result;
        return result;
      },
    };
    const runExtraction = async (options) => {
      calls.push(['run', options.timeoutMs]);
      workDirDuringRun = (await readdir(workDir)).length;
      return behavior.run(options);
    };
    const record = (level) => (event, fields) => logs.push({ level, event, ...fields });
    const log = { info: record('info'), warn: record('warn'), error: record('error') };
    return createJobHandler({
      config: { bucket: BUCKET, prefix: PREFIX, environments: ['dev', 'prod'], workDir, extractionTimeoutMs: 480_000 },
      s3,
      callbacks,
      runExtraction,
      log,
      now: () => 1000,
    });
  }
  let workDirDuringRun;

  const handle = (handler, { body = eventBody(), receiveCount = 1, signal } = {}) => handler(body, { messageId: 'm-1', receiveCount, signal });
  const names = () => calls.map((call) => call[0]);
  const lastFailed = () => calls.filter((call) => call[0] === 'failed').at(-1)?.[3];
  const noJobDirs = async () => assert.deepEqual(await readdir(workDir), []);

  test('0. 시험 이벤트·잘못된 키는 콜백·S3 호출 없이 delete, message_ignored 로그', async () => {
    const handler = harness();
    assert.equal(await handle(handler, { body: JSON.stringify({ Event: 's3:TestEvent' }) }), 'delete');
    assert.equal(await handle(handler, { body: eventBody({ key: 'chalkak/filter-probes/dev/not-a-uuid.jpg' }) }), 'delete');
    assert.deepEqual(calls, []);
    assert.deepEqual(logs.map((l) => [l.level, l.event, l.reason]), [['info', 'message_ignored', 'test_event'], ['warn', 'message_ignored', 'unexpected_key']]);
  });

  test('1. 정상: start -> 내려받기 -> 추출 -> 레시피 쓰기 -> complete(분기 홀드아웃 하나) -> delete', async () => {
    const handler = harness();
    assert.equal(await handle(handler), 'delete');
    assert.deepEqual(names(), ['start', 'get', 'run', 'put', 'complete']);
    assert.deepEqual(calls[0], ['start', 'dev', PROBE_ID]);
    assert.deepEqual(calls[1], ['get', KEY]);
    assert.equal(calls[2][1], 480_000);
    const [, key, contentType, cacheControl, body] = calls[3];
    assert.equal(key, `chalkak/filters/dev/recipes/${RECIPE_ID}.json`);
    assert.equal(contentType, 'application/json');
    assert.equal(cacheControl, 'public, max-age=31536000, immutable');
    assert.equal(body, OK_RESULT.recipeBytes);
    const complete = calls[4][3];
    assert.deepEqual(Object.keys(complete), ['extractorVersion', 'probeVersion', 'branch', 'holdoutDeltaE', 'warningMessages', 'xmpSummary']);
    assert.deepEqual(complete, { extractorVersion: '1.0.0', probeVersion: 2, branch: 'A2', holdoutDeltaE: 0.5, warningMessages: ['경고 문구'], xmpSummary: META.xmpSummary });
    assert.deepEqual(logs.map((l) => l.event), ['extraction_finished', 'recipe_written', 'job_finished']);
    assert.equal(logs[0].warnings, 1);
    assert.deepEqual(logs[0].holdout, META.holdout);
    assert.equal(logs.at(-1).outcome, 'delete');
    assert.equal(workDirDuringRun, 1); // 작업 중에는 작업 폴더가 있다
    await noJobDirs();
  });

  test('1b. prod 키는 prod 환경으로 콜백하고 prod 레시피 경로에 쓴다', async () => {
    const handler = harness();
    assert.equal(await handle(handler, { body: eventBody({ env: 'prod' }) }), 'delete');
    assert.deepEqual(calls[0], ['start', 'prod', PROBE_ID]);
    assert.equal(calls.find((c) => c[0] === 'put')[1], `chalkak/filters/prod/recipes/${RECIPE_ID}.json`);
  });

  test('2. 수신 5회: failed(PROCESSING_FAILED)만 보내고 start 없이 delete', async () => {
    const handler = harness();
    assert.equal(await handle(handler, { receiveCount: 5 }), 'delete');
    assert.deepEqual(names(), ['failed']);
    assert.deepEqual(lastFailed(), { type: 'PROCESSING_FAILED', message: PROCESSING_FAILED_MESSAGE });
    assert.ok(logs.some((l) => l.event === 'job_abandoned' && l.level === 'error'));
    await noJobDirs();
  });

  test('2b. 포기 경로의 failed가 재시도·영구 거부 오류여도 delete, 종료 신호면 release', async () => {
    for (const error of [callbackError('retryable', 503), callbackError('permanent', 400), new Error('예상 밖')]) {
      calls.length = 0;
      assert.equal(await handle(harness({ failed: async () => { throw error; } }), { receiveCount: 7 }), 'delete');
    }
    assert.equal(await handle(harness({ failed: async () => { throw callbackError('aborted'); } }), { receiveCount: 5 }), 'release');
  });

  test('3. start proceed false -> delete, 내려받기 없음', async () => {
    const handler = harness({ start: async () => ({ proceed: false, recipeId: null }) });
    assert.equal(await handle(handler), 'delete');
    assert.deepEqual(names(), ['start']);
    assert.ok(logs.some((l) => l.event === 'start_skipped'));
  });

  test('3b. start의 어떤 오류도 retry (종료 신호만 release)', async () => {
    for (const error of [callbackError('permanent', 404), callbackError('retryable', 401), callbackError('retryable', 503), callbackError('retryable'), callbackError('invalid-response'), new Error('예상 밖')]) {
      assert.equal(await handle(harness({ start: async () => { throw error; } })), 'retry', String(error));
    }
    assert.equal(await handle(harness({ start: async () => { throw callbackError('aborted'); } })), 'release');
    assert.ok(!names().includes('get'));
  });

  test('4. 이벤트 size가 30MB를 넘으면 failed(INVALID_PROBE, 크기 문구), 내려받기 없음 -> delete', async () => {
    const handler = harness();
    assert.equal(await handle(handler, { body: eventBody({ size: DEFAULT_MAX_BYTES + 1 }) }), 'delete');
    assert.deepEqual(names(), ['start', 'failed']);
    assert.deepEqual(lastFailed(), { type: 'INVALID_PROBE', message: TOO_LARGE_MESSAGE });
    // 정확히 상한이면 통과한다
    calls.length = 0;
    await handle(harness(), { body: eventBody({ size: DEFAULT_MAX_BYTES }) });
    assert.ok(names().includes('get'));
  });

  test('5. 내려받기: too-large -> INVALID_PROBE, missing -> PROCESSING_FAILED, 그 밖 -> retry', async () => {
    const tooLarge = harness({ get: () => ({ Body: Readable.from([Buffer.alloc(1)]), ContentLength: DEFAULT_MAX_BYTES + 1 }) });
    assert.equal(await handle(tooLarge), 'delete');
    assert.deepEqual(lastFailed(), { type: 'INVALID_PROBE', message: TOO_LARGE_MESSAGE });

    assert.equal(await handle(harness({ get: () => awsError('NoSuchKey') })), 'delete');
    assert.deepEqual(lastFailed(), { type: 'PROCESSING_FAILED', message: PROCESSING_FAILED_MESSAGE });

    calls.length = 0;
    assert.equal(await handle(harness({ get: () => awsError('AccessDenied') })), 'retry');
    assert.ok(!names().includes('failed') && !names().includes('run'));
    assert.ok(logs.some((l) => l.event === 'job_step_failed' && l.stage === 'download'));
    await noJobDirs();
  });

  test('6. 추출: invalid -> INVALID_PROBE(자식 문구), timeout -> PROCESSING_FAILED, error·crash -> 콜백 없이 retry, aborted -> release', async () => {
    assert.equal(await handle(harness({ run: async () => ({ kind: 'invalid', message: '나쁜 견본이에요.', durationMs: 5 }) })), 'delete');
    assert.deepEqual(lastFailed(), { type: 'INVALID_PROBE', message: '나쁜 견본이에요.' });
    assert.ok(logs.some((l) => l.event === 'extraction_rejected'));

    assert.equal(await handle(harness({ run: async () => ({ kind: 'timeout', durationMs: 480_000 }) })), 'delete');
    assert.deepEqual(lastFailed(), { type: 'PROCESSING_FAILED', message: PROCESSING_FAILED_MESSAGE });
    assert.ok(logs.some((l) => l.event === 'extraction_failed' && l.kind === 'timeout'));

    for (const kind of ['error', 'crash']) {
      calls.length = 0;
      assert.equal(await handle(harness({ run: async () => ({ kind, exitCode: null, signal: 'SIGKILL', cause: '메모리 부족', durationMs: 9 }) })), 'retry');
      assert.deepEqual(names(), ['start', 'get', 'run']);
    }
    assert.ok(logs.some((l) => l.event === 'extraction_failed' && l.kind === 'crash' && l.cause === '메모리 부족'));

    assert.equal(await handle(harness({ run: async () => ({ kind: 'aborted', durationMs: 1 }) })), 'release');
    await noJobDirs();
  });

  test('7. 레시피 쓰기 실패 -> complete 없이 retry', async () => {
    assert.equal(await handle(harness({ put: () => awsError('AccessDenied') })), 'retry');
    assert.ok(!names().includes('complete'));
    await noJobDirs();
  });

  test('8. complete: 재시도 가능 오류는 retry, 영구 거부는 failed(PROCESSING_FAILED) 후 delete', async () => {
    assert.equal(await handle(harness({ complete: async () => { throw callbackError('retryable', 503); } })), 'retry');
    assert.ok(!names().includes('failed'));

    calls.length = 0;
    assert.equal(await handle(harness({ complete: async () => { throw callbackError('permanent', 400); } })), 'delete');
    assert.deepEqual(names().slice(-2), ['complete', 'failed']);
    assert.deepEqual(lastFailed(), { type: 'PROCESSING_FAILED', message: PROCESSING_FAILED_MESSAGE });
    assert.ok(logs.some((l) => l.event === 'callback_permanently_refused' && l.action === 'complete'));

    // 닫는 failed도 재시도 오류면 retry
    assert.equal(await handle(harness({
      complete: async () => { throw callbackError('permanent', 400); },
      failed: async () => { throw callbackError('retryable', 503); },
    })), 'retry');

    assert.equal(await handle(harness({ complete: async () => { throw callbackError('aborted'); } })), 'release');
  });

  test('9. failed 영구 거부 -> 오류 로그 후 delete, 재시도 오류 -> retry', async () => {
    const run = { run: async () => ({ kind: 'invalid', message: '문구', durationMs: 1 }) };
    assert.equal(await handle(harness({ ...run, failed: async () => { throw callbackError('permanent', 404); } })), 'delete');
    assert.ok(logs.some((l) => l.event === 'callback_permanently_refused' && l.action === 'failed'));
    assert.equal(await handle(harness({ ...run, failed: async () => { throw callbackError('retryable', 503); } })), 'retry');
  });

  test('10. 종료 신호로 중단된 콜백은 release', async () => {
    const controller = new AbortController();
    controller.abort();
    const handler = harness({ get: () => { throw awsError('AbortError'); } });
    assert.equal(await handle(handler, { signal: controller.signal }), 'release');
  });

  test('11. holdout[branch]가 NaN·누락이면 holdoutDeltaE는 null', async () => {
    for (const holdout of [{ M1: 0.9, A2: null, G1: 0.7 }, { M1: 0.9 }]) {
      calls.length = 0;
      await handle(harness({ run: async () => ({ ...OK_RESULT, meta: { ...META, holdout } }) }));
      assert.equal(calls.find((c) => c[0] === 'complete')[3].holdoutDeltaE, null);
    }
  });

  test('12. 모든 갈래에서 작업 폴더가 지워진다(예외 포함)', async () => {
    await handle(harness({ run: async () => { throw new Error('실행기 버그'); } }));
    await noJobDirs();
    assert.ok(logs.some((l) => l.event === 'job_step_failed' && l.stage === 'extract'));
    await handle(harness({ run: async () => ({ kind: 'crash', exitCode: null, signal: 'SIGKILL', cause: '', durationMs: 1 }) }));
    await noJobDirs();
  });

  test('로그에 시크릿·서명 같은 값이 없고 공통 필드가 붙는다', async () => {
    await handle(harness());
    for (const line of logs.filter((l) => l.event !== 'message_ignored')) {
      assert.equal(line.messageId, 'm-1');
      assert.equal(line.receiveCount, 1);
      assert.equal(line.env, 'dev');
      assert.equal(line.probeId, PROBE_ID);
    }
    assert.doesNotMatch(JSON.stringify(logs), /secret|signature|X-Chalkak/i);
  });
});
