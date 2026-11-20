// HMAC 콜백 클라이언트: 고정 서명 벡터, 응답 분류, 기준 URL 규칙, 모의 백엔드 왕복, 시도마다 새 서명
import { describe, test, after } from 'node:test';
import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import {
  CallbackError,
  createCallbackClient,
  normalizeBaseUrl,
  outcomeForCallbackError,
  toAsciiJson,
} from '../src/worker/callback-client.js';
import { startMockBackend } from './support/mock-backend.js';

const SECRET = 'chalkak-filter-worker-test-secret-0123456789';
const FIXED_NOW_MS = 1762905600 * 1000;
const PROBE_ID = '0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f';
const RECIPE_ID = '0190f5c2-aaaa-7c3e-9d2f-1a2b3c4d5e6f';
const BASE = 'https://api.example.com/internal/v1';
const BASE_URLS = { dev: 'https://dev.example.com/internal/v1', prod: BASE };
const NO_WAIT = async () => {};

// 요청을 기록하고 정해진 응답을 돌려주는 가짜 fetch
function fakeFetch(...responses) {
  const calls = [];
  const fn = async (url, init) => {
    calls.push({ url, ...init });
    const next = responses[Math.min(calls.length - 1, responses.length - 1)];
    if (next instanceof Error) throw next;
    return new Response(next.body ?? null, { status: next.status });
  };
  fn.calls = calls;
  return fn;
}

const clientWith = (fetch, extra = {}) =>
  createCallbackClient({ baseUrls: BASE_URLS, secret: SECRET, fetch, now: () => FIXED_NOW_MS, sleep: NO_WAIT, ...extra });

const rejectsWith = (promise, kind, status = null) =>
  assert.rejects(promise, (error) => error instanceof CallbackError && error.kind === kind && error.status === status);

describe('고정 서명 벡터(파이썬 hmac으로 만든 값)', () => {
  test('start, dev: 빈 본문, Content-Type 없음', async () => {
    const fetch = fakeFetch({ status: 200, body: JSON.stringify({ proceed: false, recipeId: null }) });
    await clientWith(fetch).start('dev', PROBE_ID);
    const [call] = fetch.calls;
    assert.equal(call.url, `https://dev.example.com/internal/v1/filter-probes/${PROBE_ID}/start`);
    assert.equal(call.method, 'POST');
    assert.equal(call.body, undefined);
    assert.equal(call.redirect, 'manual');
    assert.deepEqual(call.headers, {
      'X-Chalkak-Callback-Timestamp': '1762905600',
      'X-Chalkak-Callback-Signature': 'v1=330e7bf515c005d8ff18a3db601b0341a055edae06422233dbc3c96ed877fdba',
    });
  });

  test('failed, prod: 본문 바이트와 해시와 서명이 같다', async () => {
    const fetch = fakeFetch({ status: 204 });
    const message = 'JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.';
    await clientWith(fetch).failed('prod', PROBE_ID, { type: 'INVALID_PROBE', message });
    const [call] = fetch.calls;
    const escaped = message.replace(/[\u007f-￿]/g, (ch) => `\\u${ch.charCodeAt(0).toString(16).padStart(4, '0')}`);
    assert.equal(Buffer.from(call.body).toString('utf8'), `{"type":"INVALID_PROBE","message":"${escaped}"}`);
    assert.match(Buffer.from(call.body).toString('utf8'), /^[\x20-\x7e]+$/);
    assert.equal(call.headers['Content-Type'], 'application/json; charset=utf-8');
    assert.equal(call.headers['X-Chalkak-Callback-Signature'], 'v1=b668b759b37325a902b123a0cc970ec11d151076937e7a2c3f0dc25a9ae57ce3');
  });

  test('본문 해시는 ASCII 이스케이프 바이트의 고정 값', async () => {
    // 설계의 해시 f14d968e…는 위 이스케이프 바이트의 sha256이다. 서명 벡터가 같으면 해시도 같다.
    const { createHash } = await import('node:crypto');
    const bytes = toAsciiJson({ type: 'INVALID_PROBE', message: 'JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.' });
    assert.equal(createHash('sha256').update(bytes).digest('hex'), 'f14d968eda6b68efbe2d41a96441a7c6540f712b3b4e9fb76cdd3fecbfa8a77f');
  });
});

describe('본문 인코딩', () => {
  test('complete 본문은 ASCII만이고 호출한 쪽의 키 순서를 그대로 쓴다', async () => {
    const fetch = fakeFetch({ status: 204 });
    const body = {
      extractorVersion: '1.0.0', probeVersion: 2, branch: 'A2', holdoutDeltaE: 1.234,
      warningMessages: ['디헤이즈·명료도는 사진에 따라 다르게 보일 수 있어요'],
      xmpSummary: { PostCropVignetteAmount: -15, Clarity2012: 10, masks: false },
    };
    await clientWith(fetch).complete('dev', PROBE_ID, body);
    const text = Buffer.from(fetch.calls[0].body).toString('utf8');
    assert.match(text, /^[\x20-\x7e]+$/);
    assert.deepEqual(Object.keys(JSON.parse(text)), Object.keys(body));
    assert.ok(text.startsWith('{"extractorVersion":"1.0.0","probeVersion":2,"branch":"A2","holdoutDeltaE":1.234,"warningMessages":["\\ub514'));
    assert.deepEqual(JSON.parse(text), body);
  });

  test('보충 평면 문자(이모지)는 서로게이트 쌍으로, 0x7f는 \\u007f로 이스케이프한다', () => {
    assert.equal(toAsciiJson('😀\x7f'), '"\\ud83d\\ude00\\u007f"');
  });

  test('유한하지 않은 수는 null이 된다', () => {
    assert.equal(toAsciiJson({ holdoutDeltaE: NaN }), '{"holdoutDeltaE":null}');
  });
});

describe('start 응답 검사', () => {
  const startWith = (response) => clientWith(fakeFetch(response)).start('dev', PROBE_ID);

  test('proceed true와 UUID, proceed false는 성공', async () => {
    assert.deepEqual(await startWith({ status: 200, body: JSON.stringify({ proceed: true, recipeId: RECIPE_ID }) }), { proceed: true, recipeId: RECIPE_ID });
    assert.deepEqual(await startWith({ status: 200, body: JSON.stringify({ proceed: false, recipeId: null }) }), { proceed: false, recipeId: null });
  });

  test('JSON이 아니거나 proceed가 문자열이거나 recipeId가 대문자·누락이면 invalid-response', async () => {
    for (const body of [
      'not json', '[]', 'null', '{}',
      JSON.stringify({ proceed: 'true', recipeId: RECIPE_ID }),
      JSON.stringify({ proceed: true, recipeId: RECIPE_ID.toUpperCase() }),
      JSON.stringify({ proceed: true }),
      JSON.stringify({ proceed: true, recipeId: null }),
    ]) {
      await rejectsWith(startWith({ status: 200, body }), 'invalid-response');
    }
  });
});

describe('상태 분류', () => {
  test('200과 204는 성공', async () => {
    assert.deepEqual(await clientWith(fakeFetch({ status: 200 })).complete('dev', PROBE_ID, {}), { status: 200 });
    assert.deepEqual(await clientWith(fakeFetch({ status: 204 })).failed('dev', PROBE_ID, {}), { status: 204 });
  });

  test('400·404·405·413·414·415는 permanent이고 재시도하지 않는다', async () => {
    for (const status of [400, 404, 405, 413, 414, 415]) {
      const fetch = fakeFetch({ status });
      await rejectsWith(clientWith(fetch).complete('dev', PROBE_ID, {}), 'permanent', status);
      assert.equal(fetch.calls.length, 1);
    }
  });

  test('401·403·408·429·500·503·302는 retryable', async () => {
    for (const status of [401, 403, 408, 429, 500, 503, 302]) {
      await rejectsWith(clientWith(fakeFetch({ status })).start('dev', PROBE_ID), 'retryable', status);
    }
  });

  test('네트워크 오류는 retryable(status 없음)', async () => {
    await rejectsWith(clientWith(fakeFetch(new TypeError('fetch failed'))).start('dev', PROBE_ID), 'retryable');
  });

  test('연결 거부와 응답 없는 서버(제한 시간 초과)는 retryable, 리다이렉트는 따라가지 않는다', async () => {
    const hits = [];
    const target = createServer((req, res) => { hits.push('target'); res.end(); });
    const hang = createServer(() => { hits.push('hang'); });
    const redirector = createServer((req, res) => {
      res.writeHead(302, { Location: `http://127.0.0.1:${target.address().port}/elsewhere` }).end();
    });
    const listen = (server) => new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
    await Promise.all([listen(target), listen(hang), listen(redirector)]);
    after(() => { hang.closeAllConnections(); for (const server of [target, hang, redirector]) server.close(); });
    const local = (server) => ({ dev: `http://127.0.0.1:${server.address().port}/internal/v1` });
    const make = (server, extra) => createCallbackClient({ baseUrls: local(server), secret: SECRET, sleep: NO_WAIT, ...extra });

    // 열려 있다 닫힌 포트
    const closed = createServer();
    await listen(closed);
    const closedUrls = local(closed);
    await new Promise((resolve) => closed.close(resolve));
    await rejectsWith(createCallbackClient({ baseUrls: closedUrls, secret: SECRET }).start('dev', PROBE_ID), 'retryable');

    const started = Date.now();
    await rejectsWith(make(hang, { timeoutMs: 150 }).start('dev', PROBE_ID), 'retryable');
    assert.ok(Date.now() - started < 3000);

    await rejectsWith(make(redirector).start('dev', PROBE_ID), 'retryable', 302);
    assert.deepEqual(hits.filter((hit) => hit === 'target'), []);
  });
});

describe('기준 URL 규칙', () => {
  test('https, 끝 /, 루프백 http는 허용', () => {
    assert.equal(normalizeBaseUrl(BASE), BASE);
    assert.equal(normalizeBaseUrl(`${BASE}/`), BASE);
    assert.equal(normalizeBaseUrl('http://127.0.0.1:8787/internal/v1'), 'http://127.0.0.1:8787/internal/v1');
    assert.equal(normalizeBaseUrl('http://localhost:8787/internal/v1/'), 'http://localhost:8787/internal/v1');
    assert.equal(normalizeBaseUrl('http://[::1]:8787/internal/v1'), 'http://[::1]:8787/internal/v1');
  });

  test('루프백이 아닌 http, 다른 경로, 사용자 정보, 쿼리, 조각은 거부', () => {
    for (const value of [
      'http://example.com/internal/v1',
      'http://127.0.0.1.example.com/internal/v1',
      'https://api.example.com/api',
      'https://api.example.com',
      'https://api.example.com/internal/v1/extra',
      'https://user:pw@api.example.com/internal/v1',
      'https://api.example.com/internal/v1?x=1',
      'https://api.example.com/internal/v1?',
      'https://api.example.com/internal/v1#frag',
      'not a url',
      '',
    ]) {
      assert.throws(() => normalizeBaseUrl(value), Error, value);
    }
  });

  test('생성할 때 검증하고, 설정에 없는 환경과 UUID가 아닌 probeId는 오류', async () => {
    assert.throws(() => createCallbackClient({ baseUrls: { dev: 'https://api.example.com/api' }, secret: SECRET }));
    const client = clientWith(fakeFetch({ status: 204 }));
    await assert.rejects(client.failed('staging', PROBE_ID, {}), /설정에 없는 환경/);
    await assert.rejects(client.failed('dev', PROBE_ID.toUpperCase(), {}), /소문자 UUID/);
  });
});

describe('모의 백엔드 왕복', () => {
  const mocks = [];
  const startMock = async (environment, behavior = {}) => {
    const mock = await startMockBackend({ secret: SECRET, environment, behavior });
    mocks.push(mock);
    return mock;
  };
  after(() => Promise.all(mocks.map((mock) => mock.close())));
  const clientFor = (mock, environment, extra = {}) =>
    createCallbackClient({ baseUrls: { [environment]: mock.baseUrl }, secret: SECRET, sleep: NO_WAIT, ...extra });

  test('start·complete·failed가 서명 검증을 통과하고 본문이 그대로 닿는다', async () => {
    for (const environment of ['dev', 'prod']) {
      const mock = await startMock(environment);
      const client = clientFor(mock, environment);
      const started = await client.start(environment, PROBE_ID);
      assert.equal(started.proceed, true);
      assert.equal(started.recipeId, mock.recipeIds.get(PROBE_ID));
      await client.complete(environment, PROBE_ID, { extractorVersion: '1.0.0', warningMessages: ['한국어 경고'] });
      await client.failed(environment, PROBE_ID, { type: 'PROCESSING_FAILED', message: '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.' });
      assert.deepEqual(mock.requests.map(({ action, signatureValid }) => [action, signatureValid]), [
        ['start', true], ['complete', true], ['failed', true],
      ]);
      assert.equal(mock.requests[0].contentType, null);
      assert.equal(mock.requests[0].rawBody.length, 0);
      assert.deepEqual(mock.requests[1].body, { extractorVersion: '1.0.0', warningMessages: ['한국어 경고'] });
      assert.equal(mock.requests[2].body.message, '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.');
    }
  });

  test('시크릿이 다르면 401이고 retryable, 환경이 다르면 서명이 맞지 않는다', async () => {
    const mock = await startMock('dev');
    const wrongSecret = createCallbackClient({ baseUrls: { dev: mock.baseUrl }, secret: `${SECRET}-other`, sleep: NO_WAIT });
    await rejectsWith(wrongSecret.start('dev', PROBE_ID), 'retryable', 401);
    // dev 서버에 prod로 서명한 요청: 같은 주소를 prod 기준 URL로 쓴다
    const wrongEnvironment = createCallbackClient({ baseUrls: { prod: mock.baseUrl }, secret: SECRET, sleep: NO_WAIT });
    await rejectsWith(wrongEnvironment.start('prod', PROBE_ID), 'retryable', 401);
    assert.deepEqual(mock.requests.map((request) => request.signatureValid), [false, false]);
  });

  test('모의 백엔드의 시각 검사: 과거 300초, 미래 30초', async () => {
    const aligned = await startMockBackend({ secret: SECRET, environment: 'dev', now: () => FIXED_NOW_MS });
    mocks.push(aligned);
    const through = (offset) => createCallbackClient({ baseUrls: { dev: aligned.baseUrl }, secret: SECRET, sleep: NO_WAIT, now: () => FIXED_NOW_MS + offset * 1000 });
    await through(-300).start('dev', PROBE_ID);
    await rejectsWith(through(-301).start('dev', PROBE_ID), 'retryable', 401);
    await through(30).start('dev', PROBE_ID);
    await rejectsWith(through(31).start('dev', PROBE_ID), 'retryable', 401);
  });

  test('모의 백엔드의 상태 코드 동작', async () => {
    const mock = await startMock('dev', { start: 503, complete: 400, failed: 500 });
    const client = clientFor(mock, 'dev');
    await rejectsWith(client.start('dev', PROBE_ID), 'retryable', 503);
    await rejectsWith(client.complete('dev', PROBE_ID, {}), 'permanent', 400);
    await rejectsWith(client.failed('dev', PROBE_ID, {}), 'retryable', 500);
    const skipping = await startMock('dev', { start: 'skip' });
    assert.deepEqual(await clientFor(skipping, 'dev').start('dev', PROBE_ID), { proceed: false, recipeId: null });
  });
});

describe('재시도와 새 서명', () => {
  test('시도마다 시계를 다시 읽어 새로 서명하고, 대기는 5초 뒤 15초', async () => {
    let clock = FIXED_NOW_MS;
    const waits = [];
    const fetch = fakeFetch({ status: 503 }, { status: 503 }, { status: 204 });
    const client = clientWith(fetch, {
      now: () => clock,
      sleep: async (ms) => { waits.push(ms); clock += ms; },
    });
    assert.deepEqual(await client.complete('dev', PROBE_ID, { a: 1 }), { status: 204 });
    assert.deepEqual(waits, [5000, 15000]);
    assert.equal(fetch.calls.length, 3);
    const timestamps = fetch.calls.map((call) => call.headers['X-Chalkak-Callback-Timestamp']);
    assert.deepEqual(timestamps, ['1762905600', '1762905605', '1762905620']);
    assert.equal(new Set(fetch.calls.map((call) => call.headers['X-Chalkak-Callback-Signature'])).size, 3);
  });

  test('세 번 모두 실패하면 마지막 오류를 던진다(대기는 두 번)', async () => {
    const waits = [];
    const fetch = fakeFetch({ status: 500 });
    const client = clientWith(fetch, { sleep: async (ms) => { waits.push(ms); } });
    await rejectsWith(client.failed('dev', PROBE_ID, {}), 'retryable', 500);
    assert.equal(fetch.calls.length, 3);
    assert.deepEqual(waits, [5000, 15000]);
  });

  test('실패한 시도마다 onAttemptFailed를 부른다', async () => {
    const seen = [];
    const client = clientWith(fakeFetch({ status: 401 }, { status: 401 }, { status: 204 }), { onAttemptFailed: (info) => seen.push([info.action, info.attempt, info.kind, info.status]) });
    await client.complete('dev', PROBE_ID, {});
    assert.deepEqual(seen, [['complete', 1, 'retryable', 401], ['complete', 2, 'retryable', 401]]);
  });

  test('재시도 대기 중 종료 신호를 받으면 aborted', async () => {
    const controller = new AbortController();
    const fetch = fakeFetch({ status: 503 });
    const client = createCallbackClient({ baseUrls: BASE_URLS, secret: SECRET, fetch, now: () => FIXED_NOW_MS });
    const pending = client.complete('dev', PROBE_ID, {}, { signal: controller.signal });
    setTimeout(() => controller.abort(), 20);
    await rejectsWith(pending, 'aborted');
    assert.equal(fetch.calls.length, 1);
  });
});

describe('재시도 보강', () => {
  test('시작 전에 이미 종료 신호가 있으면 요청 없이 aborted', async () => {
    const fetch = fakeFetch({ status: 204 });
    await rejectsWith(clientWith(fetch).complete('dev', PROBE_ID, {}, { signal: AbortSignal.abort() }), 'aborted');
    assert.equal(fetch.calls.length, 0);
  });

  test('onAttemptFailed가 던져도 재시도는 계속된다', async () => {
    const fetch = fakeFetch({ status: 503 }, { status: 204 });
    const client = clientWith(fetch, { onAttemptFailed: () => { throw new Error('hook'); } });
    assert.deepEqual(await client.complete('dev', PROBE_ID, {}), { status: 204 });
    assert.equal(fetch.calls.length, 2);
  });
});

describe('콜백 실패 뒤 메시지 처리(README 1-5절 표)', () => {
  const error = (kind) => new CallbackError(kind, 'x');

  test('start는 어떤 오류든 남긴다', () => {
    for (const kind of ['permanent', 'retryable', 'invalid-response']) {
      assert.equal(outcomeForCallbackError('start', error(kind)), 'retry');
    }
  });

  test('complete는 영구 거부면 닫고, 아니면 남긴다', () => {
    assert.equal(outcomeForCallbackError('complete', error('permanent')), 'close');
    assert.equal(outcomeForCallbackError('complete', error('retryable')), 'retry');
  });

  test('failed는 영구 거부면 지우고, 아니면 남긴다', () => {
    assert.equal(outcomeForCallbackError('failed', error('permanent')), 'delete');
    assert.equal(outcomeForCallbackError('failed', error('retryable')), 'retry');
  });

  test('종료 신호로 멈췄으면 반납한다', () => {
    for (const action of ['start', 'complete', 'failed']) {
      assert.equal(outcomeForCallbackError(action, error('aborted')), 'release');
    }
  });
});
