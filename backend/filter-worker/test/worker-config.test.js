// 워커 설정 검증과 진입점의 종료 코드(78, 신호 종료 0)
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdtemp, mkdir, readdir, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { inspect } from 'node:util';
import { CONFIG_VARIABLES, ConfigError, loadConfig } from '../src/worker/config.js';
import { clientOptions, prepareWorkDir } from '../src/worker/main.js';

const MAIN = fileURLToPath(new URL('../src/worker/main.js', import.meta.url));
const SECRET = 'chalkak-filter-worker-test-secret-0123456789';
const REQUIRED = {
  FILTER_EXTRACTION_QUEUE_URL: 'https://sqs.ap-northeast-2.amazonaws.com/123456789012/chalkak-filter-extraction',
  AWS_REGION: 'ap-northeast-2',
  DEV_BACKEND_INTERNAL_API_BASE_URL: 'https://dev.example.com/internal/v1',
  PROD_BACKEND_INTERNAL_API_BASE_URL: 'https://api.example.com/internal/v1/',
  IMAGE_PROCESSING_API_SECRET: SECRET,
};

const errorFor = (env) => {
  try {
    loadConfig(env);
  } catch (error) {
    return error;
  }
  assert.fail('ConfigError가 나야 합니다.');
};

describe('워커 설정', () => {
  test('필수 값만 주면 기본값이 채워진다', () => {
    const config = loadConfig(REQUIRED);
    assert.equal(config.bucket, 'techcourse-project-2026');
    assert.equal(config.prefix, 'chalkak');
    assert.deepEqual(config.environments, ['dev', 'prod']);
    assert.deepEqual(config.baseUrls, { dev: 'https://dev.example.com/internal/v1', prod: 'https://api.example.com/internal/v1' });
    assert.equal(config.callbackTimeoutMs, 5000);
    assert.equal(config.maxReceiveCount, 5);
    assert.equal(config.extractionTimeoutMs, 480_000);
    assert.equal(config.heartbeatIntervalMs, 60_000);
    assert.equal(config.visibilityExtensionSeconds, 180);
    assert.equal(config.workDir, tmpdir());
  });

  test('접두사는 앞뒤 /를 지운다', () => {
    assert.equal(loadConfig({ ...REQUIRED, S3_PREFIX: '/chalkak/' }).prefix, 'chalkak');
    assert.equal(errorFor({ ...REQUIRED, S3_PREFIX: '//' }).variable, 'S3_PREFIX');
  });

  test('필수 변수가 하나씩 빠지면 그 이름을 담은 ConfigError이고 문구에 시크릿이 없다', () => {
    for (const name of Object.keys(REQUIRED)) {
      const { [name]: _removed, ...rest } = REQUIRED;
      const error = errorFor(rest);
      assert.ok(error instanceof ConfigError);
      assert.equal(error.variable, name);
      assert.ok(!error.message.includes(SECRET));
    }
  });

  test('값이 잘못되면 변수 이름만 담고 값은 담지 않는다', () => {
    const cases = [
      ['IMAGE_PROCESSING_API_SECRET', 'x'.repeat(31)],
      ['FILTER_EXTRACTION_QUEUE_URL', 'http://sqs.example.com/q'],
      ['DEV_BACKEND_INTERNAL_API_BASE_URL', 'http://example.com/internal/v1'],
      ['PROD_BACKEND_INTERNAL_API_BASE_URL', 'https://api.example.com/api'],
      ['SQS_MAX_RECEIVE_COUNT', '0'],
      ['EXTRACTION_TIMEOUT_SECONDS', '-1'],
      ['EXTRACTION_TIMEOUT_SECONDS', 'abc'],
      ['CALLBACK_TIMEOUT_SECONDS', '0'],
      ['HEARTBEAT_INTERVAL_SECONDS', '90'],
      ['VISIBILITY_EXTENSION_SECONDS', '43201'],
    ];
    for (const [name, value] of cases) {
      const error = errorFor({ ...REQUIRED, [name]: value });
      assert.equal(error.variable, name, `${name}=${value}`);
      assert.ok(!error.message.includes(value) || value.length < 4, `${name} 값이 문구에 있습니다.`);
    }
  });

  test('루프백 http 기준 URL은 허용한다', () => {
    const config = loadConfig({ ...REQUIRED, DEV_BACKEND_INTERNAL_API_BASE_URL: 'http://127.0.0.1:8787/internal/v1' });
    assert.equal(config.baseUrls.dev, 'http://127.0.0.1:8787/internal/v1');
  });

  test('JSON.stringify와 util.inspect에 시크릿이 없다', () => {
    const config = loadConfig(REQUIRED);
    assert.ok(!JSON.stringify(config).includes(SECRET));
    assert.ok(!inspect(config, { depth: 5 }).includes(SECRET));
    assert.ok(!`${JSON.stringify({ config })}`.includes(SECRET));
  });

  test('설정이 읽는 변수 목록이 CONFIG_VARIABLES와 같다', () => {
    const read = new Set();
    const env = new Proxy({}, { get: (_target, name) => (read.add(name), REQUIRED[name]), has: () => true });
    loadConfig(env);
    assert.deepEqual([...read].sort(), [...CONFIG_VARIABLES].sort());
  });
});

describe('작업 폴더 준비', () => {
  test('만들고, 남은 job-*만 지운다', async () => {
    const root = await mkdtemp(join(tmpdir(), 'worker-workdir-'));
    try {
      const workDir = join(root, 'work');
      await mkdir(join(workDir, 'job-old'), { recursive: true });
      await mkdir(join(workDir, 'other'));
      await prepareWorkDir(workDir);
      assert.deepEqual(await readdir(workDir), ['other']);
      await prepareWorkDir(join(workDir, 'new'));
      assert.ok((await readdir(workDir)).includes('new'));
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

  test('쓸 수 없는 위치면 FILTER_WORKER_WORK_DIR ConfigError', async () => {
    await assert.rejects(prepareWorkDir('/proc/chalkak-nope/work'), (error) => error instanceof ConfigError && error.variable === 'FILTER_WORKER_WORK_DIR');
  });
});

describe('진입점 main.js', () => {
  test('AWS 클라이언트는 재시도 1회와 30초 제한 시간으로 종료 시간 안에 끝난다', () => {
    assert.deepEqual(clientOptions('ap-northeast-2'), { region: 'ap-northeast-2', maxAttempts: 2, requestHandler: { requestTimeout: 30_000 } });
  });

  test('빈 환경이면 종료 78과 config_invalid JSON 한 줄', () => {
    const result = spawnSync(process.execPath, [MAIN], { env: { PATH: process.env.PATH }, encoding: 'utf8' });
    assert.equal(result.status, 78);
    const lines = result.stdout.trim().split('\n');
    assert.equal(lines.length, 1);
    const entry = JSON.parse(lines[0]);
    assert.equal(entry.event, 'config_invalid');
    assert.equal(entry.level, 'error');
    assert.equal(entry.variable, 'FILTER_EXTRACTION_QUEUE_URL');
  });

  test('시크릿이 짧으면 78이고 출력에 시크릿이 없다', () => {
    const short = 'short-secret';
    const env = { PATH: process.env.PATH, ...REQUIRED, IMAGE_PROCESSING_API_SECRET: short };
    const result = spawnSync(process.execPath, [MAIN], { env, encoding: 'utf8' });
    assert.equal(result.status, 78);
    assert.ok(!result.stdout.includes(short));
  });

  test('SIGTERM이면 오류 대기를 끊고 worker_stopped를 남기고 종료 0', async () => {
    // 예약된 .invalid 도메인이라 수신이 바로 실패하고 워커는 오류 대기에 들어간다(네트워크를 쓰지 않는다)
    const workDir = await mkdtemp(join(tmpdir(), 'worker-main-'));
    const env = {
      PATH: process.env.PATH,
      ...REQUIRED,
      FILTER_EXTRACTION_QUEUE_URL: 'https://sqs.chalkak.invalid/123456789012/chalkak-filter-extraction',
      FILTER_WORKER_WORK_DIR: workDir,
      AWS_ACCESS_KEY_ID: 'test',
      AWS_SECRET_ACCESS_KEY: 'test',
    };
    const child = spawn(process.execPath, [MAIN], { env, stdio: ['ignore', 'pipe', 'inherit'] });
    let stdout = '';
    const failed = Promise.withResolvers();
    child.stdout.on('data', (chunk) => {
      stdout += chunk;
      if (stdout.includes('receive_failed')) failed.resolve();
    });
    const exited = new Promise((resolve) => child.on('exit', (code, signal) => resolve({ code, signal })));
    try {
      await failed.promise;
      child.kill('SIGTERM');
      const { code, signal } = await exited;
      assert.equal(signal, null);
      assert.equal(code, 0);
      const events = stdout.trim().split('\n').map((line) => JSON.parse(line).event);
      assert.deepEqual(events, ['worker_started', 'receive_failed', 'worker_stopping', 'worker_stopped']);
      assert.ok(!stdout.includes(SECRET));
    } finally {
      child.kill('SIGKILL');
      await rm(workDir, { recursive: true, force: true });
    }
  });
});
