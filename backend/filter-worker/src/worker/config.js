// 환경 변수에서 워커 설정을 읽고 검증한다(README 설정 절). 잘못되면 ConfigError(변수 이름만, 값은 담지 않는다).
// main.js는 ConfigError를 종료 코드 78로 바꾼다. systemd가 RestartPreventExitStatus=78로 재시작 회전을 막는다.
import { tmpdir } from 'node:os';
import { inspect } from 'node:util';
import { normalizeBaseUrl } from './callback-client.js';

export const CONFIG_EXIT_CODE = 78;
export const ENVIRONMENTS = ['dev', 'prod'];
const MIN_SECRET_LENGTH = 32;

// deploy/filter-worker.env.example의 변수 목록과 같아야 한다(test/worker-deploy-files.test.js가 확인)
export const CONFIG_VARIABLES = [
  'FILTER_EXTRACTION_QUEUE_URL',
  'AWS_REGION',
  'S3_BUCKET',
  'S3_PREFIX',
  'DEV_BACKEND_INTERNAL_API_BASE_URL',
  'PROD_BACKEND_INTERNAL_API_BASE_URL',
  'IMAGE_PROCESSING_API_SECRET',
  'CALLBACK_TIMEOUT_SECONDS',
  'SQS_MAX_RECEIVE_COUNT',
  'EXTRACTION_TIMEOUT_SECONDS',
  'HEARTBEAT_INTERVAL_SECONDS',
  'VISIBILITY_EXTENSION_SECONDS',
  'FILTER_WORKER_WORK_DIR',
];

export class ConfigError extends Error {
  constructor(variable, reason) {
    super(`환경 변수 ${variable}: ${reason}`);
    this.name = 'ConfigError';
    this.variable = variable;
  }
}

const blank = (value) => value === undefined || value.trim() === '';

function required(env, name) {
  if (blank(env[name])) throw new ConfigError(name, '값이 필요합니다.');
  return env[name].trim();
}

function withDefault(env, name, fallback) {
  return blank(env[name]) ? fallback : env[name].trim();
}

function positiveInteger(env, name, fallback) {
  const text = withDefault(env, name, String(fallback));
  if (!/^[0-9]+$/.test(text) || Number(text) < 1 || !Number.isSafeInteger(Number(text))) {
    throw new ConfigError(name, '1 이상의 정수여야 합니다.');
  }
  return Number(text);
}

function positiveNumber(env, name, fallback) {
  const text = withDefault(env, name, String(fallback));
  const value = Number(text);
  if (!/^[0-9]+(\.[0-9]+)?$/.test(text) || !Number.isFinite(value) || value <= 0) {
    throw new ConfigError(name, '0보다 큰 숫자여야 합니다.');
  }
  return value;
}

function baseUrl(env, name) {
  const value = required(env, name);
  try {
    return normalizeBaseUrl(value);
  } catch (error) {
    throw new ConfigError(name, error.message);
  }
}

export function loadConfig(env = process.env) {
  const queueUrl = required(env, 'FILTER_EXTRACTION_QUEUE_URL');
  if (!queueUrl.startsWith('https://sqs.')) throw new ConfigError('FILTER_EXTRACTION_QUEUE_URL', 'https://sqs.로 시작해야 합니다.');

  const region = required(env, 'AWS_REGION');
  const bucket = withDefault(env, 'S3_BUCKET', 'techcourse-project-2026');
  const prefix = withDefault(env, 'S3_PREFIX', 'chalkak').replace(/^\/+|\/+$/g, '');
  if (prefix === '') throw new ConfigError('S3_PREFIX', '비어 있을 수 없습니다.');

  const baseUrls = {
    dev: baseUrl(env, 'DEV_BACKEND_INTERNAL_API_BASE_URL'),
    prod: baseUrl(env, 'PROD_BACKEND_INTERNAL_API_BASE_URL'),
  };

  const secret = required(env, 'IMAGE_PROCESSING_API_SECRET');
  if (secret.length < MIN_SECRET_LENGTH) {
    throw new ConfigError('IMAGE_PROCESSING_API_SECRET', `${MIN_SECRET_LENGTH}자 이상이어야 합니다.`);
  }

  const callbackTimeoutMs = Math.round(positiveNumber(env, 'CALLBACK_TIMEOUT_SECONDS', 5) * 1000);
  const maxReceiveCount = positiveInteger(env, 'SQS_MAX_RECEIVE_COUNT', 5);
  const extractionTimeoutMs = positiveInteger(env, 'EXTRACTION_TIMEOUT_SECONDS', 480) * 1000;
  const heartbeatIntervalSeconds = positiveInteger(env, 'HEARTBEAT_INTERVAL_SECONDS', 60);
  const visibilityExtensionSeconds = positiveInteger(env, 'VISIBILITY_EXTENSION_SECONDS', 180);
  if (visibilityExtensionSeconds > 43200) throw new ConfigError('VISIBILITY_EXTENSION_SECONDS', '43200 이하여야 합니다.');
  // 연장을 한 번 놓쳐도 숨김 시간이 끝나지 않게 한다
  if (heartbeatIntervalSeconds * 2 >= visibilityExtensionSeconds) {
    throw new ConfigError('HEARTBEAT_INTERVAL_SECONDS', 'VISIBILITY_EXTENSION_SECONDS의 절반보다 작아야 합니다.');
  }
  const workDir = withDefault(env, 'FILTER_WORKER_WORK_DIR', tmpdir());

  const config = {
    queueUrl,
    region,
    bucket,
    prefix,
    environments: [...ENVIRONMENTS],
    baseUrls,
    secret,
    callbackTimeoutMs,
    maxReceiveCount,
    extractionTimeoutMs,
    heartbeatIntervalMs: heartbeatIntervalSeconds * 1000,
    visibilityExtensionSeconds,
    workDir,
  };
  // 로그나 오류에 설정 객체가 통째로 찍혀도 시크릿이 남지 않게 한다
  const redacted = () => ({ ...config, secret: '[redacted]' });
  Object.defineProperty(config, 'toJSON', { value: redacted, enumerable: false });
  Object.defineProperty(config, inspect.custom, { value: (depth, options) => inspect(redacted(), options), enumerable: false });
  return Object.freeze(config);
}
