// 백엔드 내부 콜백(start·complete·failed)을 HMAC으로 서명해 보내는 클라이언트.
// 서명 형식은 백엔드 ProcessingCallbackAuthenticator와 같다(README "콜백 계약" 참고).
import { createHmac, createHash } from 'node:crypto';
import { setTimeout as delay } from 'node:timers/promises';

const API_PATH = '/internal/v1';
const LOOPBACK_HOSTS = new Set(['127.0.0.1', 'localhost', '[::1]']);
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const ACTIONS = new Set(['start', 'complete', 'failed']);
// start는 영구 거부로 보지 않으므로 이 분류는 complete·failed에만 의미가 있다
const PERMANENT_STATUSES = new Set([400, 404, 405, 413, 414, 415]);

export const DEFAULT_TIMEOUT_MS = 5000;
// complete·failed는 최대 3번 시도한다. 첫 시도 뒤 5초, 15초를 기다린다
export const DEFAULT_RETRY_DELAYS_MS = [5000, 15000];

// kind: 'permanent' | 'retryable' | 'invalid-response' | 'aborted'
export class CallbackError extends Error {
  constructor(kind, message, { status = null, cause } = {}) {
    super(message, cause === undefined ? undefined : { cause });
    this.name = 'CallbackError';
    this.kind = kind;
    this.status = status;
  }
}

// 기준 URL을 검증해 끝의 /를 뺀 문자열로 돌려준다. 경로가 틀리면 모든 콜백이 401이 되고 401은 재시도 대상이라,
// 오타 하나가 끝없는 재시도처럼 보이므로 기동 때 막는다.
export function normalizeBaseUrl(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('백엔드 기준 URL 형식이 올바르지 않습니다.');
  }
  const secure = url.protocol === 'https:';
  const loopbackHttp = url.protocol === 'http:' && LOOPBACK_HOSTS.has(url.hostname);
  if (!secure && !loopbackHttp) {
    throw new Error('백엔드 기준 URL은 https여야 합니다(루프백 주소만 http 허용).');
  }
  if (url.username || url.password || url.search || url.hash || value.includes('?') || value.includes('#')) {
    throw new Error('백엔드 기준 URL에 사용자 정보, 쿼리, 조각을 넣을 수 없습니다.');
  }
  if (url.pathname.replace(/\/+$/, '') !== API_PATH) {
    throw new Error(`백엔드 기준 URL의 경로는 ${API_PATH}여야 합니다.`);
  }
  return `${url.origin}${API_PATH}`;
}

// 본문은 ASCII만 쓴다. 비ASCII는 \uXXXX(소문자 16진수)로 바꿔, 백엔드가 문자열을 다시 인코딩해도 서명이 어긋나지 않게 한다.
// 파이썬 json.dumps(ensure_ascii=True)와 같이 0x7f도 이스케이프한다.
export function toAsciiJson(value) {
  return JSON.stringify(value).replace(/[\u007f-￿]/g, (ch) => `\\u${ch.charCodeAt(0).toString(16).padStart(4, '0')}`);
}

const sha256Hex = (bytes) => createHash('sha256').update(bytes).digest('hex');

export function signCallback({ secret, timestamp, path, body, environment }) {
  const payload = `${timestamp}\nPOST\n${path}\n${sha256Hex(body)}\n${environment}`;
  return `v1=${createHmac('sha256', secret).update(payload, 'utf8').digest('hex')}`;
}

function parseStartResponse(text) {
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch (cause) {
    throw new CallbackError('invalid-response', 'start 응답이 JSON이 아닙니다.', { cause });
  }
  if (parsed === null || typeof parsed !== 'object' || typeof parsed.proceed !== 'boolean') {
    throw new CallbackError('invalid-response', 'start 응답의 proceed가 불리언이 아닙니다.');
  }
  if (!parsed.proceed) {
    return { proceed: false, recipeId: null };
  }
  if (typeof parsed.recipeId !== 'string' || !UUID_PATTERN.test(parsed.recipeId)) {
    throw new CallbackError('invalid-response', 'start 응답의 recipeId가 소문자 UUID가 아닙니다.');
  }
  return { proceed: true, recipeId: parsed.recipeId };
}

export function createCallbackClient({
  baseUrls,
  secret,
  timeoutMs = DEFAULT_TIMEOUT_MS,
  fetch: fetchImpl = globalThis.fetch,
  now = Date.now,
  sleep = (ms, signal) => delay(ms, undefined, { signal }),
  retryDelaysMs = DEFAULT_RETRY_DELAYS_MS,
  onAttemptFailed = () => {},
}) {
  // 로그 훅이 던져도 재시도 흐름이 끊기지 않게 한다
  const notify = (info) => {
    try {
      onAttemptFailed(info);
    } catch {
      // 훅 오류는 무시한다
    }
  };
  const origins = new Map(Object.entries(baseUrls).map(([env, url]) => [env, normalizeBaseUrl(url)]));

  // 시도마다 새로 서명한다. 재시도 대기 중에 시각이 밀려도 백엔드의 시각 허용 범위를 넘지 않게 한다.
  async function send(environment, probeId, action, bodyText) {
    const base = origins.get(environment);
    if (base === undefined) {
      throw new Error(`설정에 없는 환경입니다: ${environment}`);
    }
    if (!UUID_PATTERN.test(probeId)) {
      throw new Error('probeId가 소문자 UUID가 아닙니다.');
    }
    const path = `${API_PATH}/filter-probes/${probeId}/${action}`;
    const body = bodyText === undefined ? Buffer.alloc(0) : Buffer.from(bodyText, 'utf8');
    const timestamp = String(Math.floor(now() / 1000));
    const headers = {
      'X-Chalkak-Callback-Timestamp': timestamp,
      'X-Chalkak-Callback-Signature': signCallback({ secret, timestamp, path, body, environment }),
    };
    if (bodyText !== undefined) {
      headers['Content-Type'] = 'application/json; charset=utf-8';
    }

    let response;
    let text = '';
    try {
      response = await fetchImpl(`${base.slice(0, -API_PATH.length)}${path}`, {
        method: 'POST',
        headers,
        body: bodyText === undefined ? undefined : body,
        redirect: 'manual',
        signal: AbortSignal.timeout(timeoutMs),
      });
      // 연결을 놓아 주려면 본문을 끝까지 읽어야 한다. 제한 시간은 본문 읽기에도 걸린다.
      text = await response.text();
    } catch (cause) {
      throw new CallbackError('retryable', `${action} 콜백 요청에 실패했습니다.`, { cause });
    }

    const { status } = response;
    if (status >= 200 && status < 300) {
      return { status, text };
    }
    const kind = PERMANENT_STATUSES.has(status) ? 'permanent' : 'retryable';
    throw new CallbackError(kind, `${action} 콜백이 상태 코드 ${status}으로 거절됐습니다.`, { status });
  }

  async function sendWithRetry(environment, probeId, action, payload, { signal } = {}) {
    const bodyText = toAsciiJson(payload);
    if (signal?.aborted) {
      throw new CallbackError('aborted', '종료 신호로 콜백을 보내지 않았습니다.');
    }
    for (let attempt = 1; ; attempt += 1) {
      try {
        const { status } = await send(environment, probeId, action, bodyText);
        return { status };
      } catch (error) {
        if (!(error instanceof CallbackError)) throw error;
        notify({ action, attempt, kind: error.kind, status: error.status, error });
        if (error.kind !== 'retryable' || attempt > retryDelaysMs.length) throw error;
      }
      try {
        await sleep(retryDelaysMs[attempt - 1], signal);
      } catch (cause) {
        throw new CallbackError('aborted', '종료 신호로 콜백 재시도를 멈췄습니다.', { cause });
      }
    }
  }

  return {
    // start는 싸므로 재시도하지 않고 호출한 쪽이 메시지를 남긴다
    async start(environment, probeId) {
      try {
        const { text } = await send(environment, probeId, 'start');
        return parseStartResponse(text);
      } catch (error) {
        if (error instanceof CallbackError) notify({ action: 'start', attempt: 1, kind: error.kind, status: error.status, error });
        throw error;
      }
    },
    complete: (environment, probeId, body, options) => sendWithRetry(environment, probeId, 'complete', body, options),
    failed: (environment, probeId, body, options) => sendWithRetry(environment, probeId, 'failed', body, options),
  };
}

// 콜백이 실패했을 때 메시지를 어떻게 할지(README 1-5절 표).
//   'retry'   메시지를 지우지 않아 숨김 시간이 끝난 뒤 SQS가 다시 주게 한다
//   'close'   complete가 영구 거부됐다. failed(PROCESSING_FAILED)를 보내 상태를 닫고 지운다
//   'delete'  failed가 영구 거부됐다. 오류 로그 후 지운다
//   'release' 종료 신호. 숨김 0초로 반납한다
export function outcomeForCallbackError(action, error) {
  if (!ACTIONS.has(action)) throw new Error(`알 수 없는 콜백 종류입니다: ${action}`);
  if (error.kind === 'aborted') return 'release';
  if (action === 'start' || error.kind !== 'permanent') return 'retry';
  return action === 'complete' ? 'close' : 'delete';
}
