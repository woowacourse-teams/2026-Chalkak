// 필터 워커 진입점: 설정 → 클라이언트 생성 → 신호 처리 → 루프 → 종료 코드(README 설계 4-4절).
//   0  종료 신호로 정상 종료
//   78 설정 오류(systemd RestartPreventExitStatus=78)
//   1  예상 밖 오류
import { mkdir, access, readdir, readFile, rm, constants } from 'node:fs/promises';
import { join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { S3Client } from '@aws-sdk/client-s3';
import { SQSClient } from '@aws-sdk/client-sqs';
import { runExtraction } from './extract-runner.js';
import { createCallbackClient } from './callback-client.js';
import { CONFIG_EXIT_CODE, ConfigError, loadConfig } from './config.js';
import { createJobHandler } from './job.js';
import { createLogger } from './log.js';
import { runLoop } from './sqs-loop.js';

const REQUEST_TIMEOUT_MS = 30_000; // ReceiveMessage 긴 폴링(20초)보다 길어야 한다
const EXIT_GUARD_MS = 5000;
// 종료 때 삭제·반납 호출이 TimeoutStopSec(60초) 안에 끝나도록 재시도를 한 번으로 줄인다(30초 x 2)
export const clientOptions = (region) => ({ region, maxAttempts: 2, requestHandler: { requestTimeout: REQUEST_TIMEOUT_MS } });

// 작업 폴더를 만들고 쓸 수 있는지 확인한 뒤, 부모가 죽으면서 남긴 job-*를 지운다
export async function prepareWorkDir(workDir) {
  try {
    await mkdir(workDir, { recursive: true });
    await access(workDir, constants.W_OK);
  } catch {
    throw new ConfigError('FILTER_WORKER_WORK_DIR', '작업 폴더를 만들거나 쓸 수 없습니다.');
  }
  for (const name of await readdir(workDir)) {
    if (name.startsWith('job-')) await rm(join(workDir, name), { recursive: true, force: true });
  }
}

async function readVersion() {
  const packageJson = JSON.parse(await readFile(new URL('../../package.json', import.meta.url), 'utf8'));
  return packageJson.version;
}

export async function main({ env = process.env, log = createLogger(), proc = process } = {}) {
  let config;
  try {
    config = loadConfig(env);
    await prepareWorkDir(config.workDir);
  } catch (error) {
    if (!(error instanceof ConfigError)) throw error;
    log.error('config_invalid', { variable: error.variable, message: error.message });
    return CONFIG_EXIT_CODE;
  }

  const sqs = new SQSClient(clientOptions(config.region));
  const s3 = new S3Client(clientOptions(config.region));
  const callbacks = createCallbackClient({
    baseUrls: config.baseUrls,
    secret: config.secret,
    timeoutMs: config.callbackTimeoutMs,
    onAttemptFailed: ({ action, attempt, kind, status, error }) => {
      // 401·403은 시크릿이 어긋났다는 신호라 오류로 올린다
      const level = status === 401 || status === 403 ? 'error' : 'warn';
      log[level]('callback_failed', { action, attempt, kind, ...(status === null ? { error: error.cause?.name ?? error.name } : { status }) });
    },
  });
  const handle = createJobHandler({ config, s3, callbacks, runExtraction, log });

  const controller = new AbortController();
  let stopping = false;
  const stop = () => {
    if (stopping) return;
    stopping = true;
    log.info('worker_stopping', {});
    controller.abort();
  };
  proc.on('SIGTERM', stop);
  proc.on('SIGINT', stop);

  log.info('worker_started', { version: await readVersion(), queueUrl: config.queueUrl, environments: config.environments });
  await runLoop({
    sqs,
    queueUrl: config.queueUrl,
    handle,
    signal: controller.signal,
    log,
    heartbeatIntervalMs: config.heartbeatIntervalMs,
    visibilityExtensionSeconds: config.visibilityExtensionSeconds,
  });
  sqs.destroy();
  s3.destroy();
  log.info('worker_stopped', {});
  return 0;
}

const isEntryPoint = process.argv[1] !== undefined && import.meta.url === pathToFileURL(process.argv[1]).href;
if (isEntryPoint) {
  const log = createLogger();
  const crash = (error) => {
    log.error('worker_crashed', { error: `${error?.name ?? 'Error'}: ${error?.message ?? String(error)}` });
    process.exit(1);
  };
  process.on('uncaughtException', crash);
  process.on('unhandledRejection', crash);
  main({ log }).then(
    (code) => {
      process.exitCode = code;
      // 열린 연결이 남아 프로세스가 안 끝나는 경우를 막는다
      setTimeout(() => process.exit(code), EXIT_GUARD_MS).unref();
    },
    crash,
  );
}
