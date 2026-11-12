// SQS 루프: 수신 입력, 결과별 동작, 숨김 연장, 수신 오류 대기, 종료 신호. 가짜 SQS 클라이언트만 쓴다.
import { describe, test, mock } from 'node:test';
import assert from 'node:assert/strict';
import { ChangeMessageVisibilityCommand, DeleteMessageCommand, ReceiveMessageCommand } from '@aws-sdk/client-sqs';
import { runLoop } from '../src/worker/sqs-loop.js';

const QUEUE = 'https://sqs.ap-northeast-2.amazonaws.com/123456789012/chalkak-filter-extraction';
const message = (extra = {}) => ({ MessageId: 'm-1', ReceiptHandle: 'rh-1', Body: '{"x":1}', Attributes: { ApproximateReceiveCount: '3' }, ...extra });
const tick = () => new Promise((resolve) => setImmediate(resolve));

// receive 응답 목록을 차례로 돌려주는 가짜 SQS. 목록이 끝나면 stop()을 부르고 종료 신호를 기다린다
function fakeSqs({ receives, controller, failures = {} }) {
  const sent = [];
  let index = 0;
  return {
    sent,
    async send(command, options) {
      sent.push({ command, options });
      if (command instanceof ReceiveMessageCommand) {
        if (index >= receives.length) {
          controller.abort();
          const error = new Error('aborted');
          error.name = 'AbortError';
          throw error;
        }
        const next = receives[index++];
        if (next instanceof Error) throw next;
        return next;
      }
      const failure = failures[command.constructor.name];
      if (failure) throw failure;
      return {};
    },
    ofType: (type) => sent.filter(({ command }) => command instanceof type).map(({ command }) => command.input),
  };
}

function recorder() {
  const logs = [];
  const record = (level) => (event, fields) => logs.push({ level, event, ...fields });
  return { logs, log: { info: record('info'), warn: record('warn'), error: record('error') } };
}

async function run({ receives, handle = async () => 'delete', failures, extra = {} }) {
  const controller = new AbortController();
  const sqs = fakeSqs({ receives, controller, failures });
  const { logs, log } = recorder();
  await runLoop({ sqs, queueUrl: QUEUE, handle, signal: controller.signal, log, sleep: async () => {}, ...extra });
  return { sqs, logs };
}

describe('SQS 루프', () => {
  test('ReceiveMessage 입력과 중단 신호', async () => {
    const { sqs } = await run({ receives: [{}] });
    const [first] = sqs.sent;
    assert.deepEqual(first.command.input, {
      QueueUrl: QUEUE,
      MaxNumberOfMessages: 1,
      WaitTimeSeconds: 20,
      MessageSystemAttributeNames: ['ApproximateReceiveCount'],
    });
    assert.ok(first.options.abortSignal instanceof AbortSignal);
  });

  test('수신 횟수를 처리기에 넘기고, 없으면 1과 receive_count_missing', async () => {
    const seen = [];
    const handle = async (body, context) => (seen.push({ body, ...context }), 'retry');
    const { logs } = await run({
      receives: [{ Messages: [message()] }, { Messages: [message({ Attributes: undefined })] }],
      handle,
    });
    assert.deepEqual(seen.map((s) => [s.body, s.messageId, s.receiveCount]), [['{"x":1}', 'm-1', 3], ['{"x":1}', 'm-1', 1]]);
    assert.equal(logs.filter((l) => l.event === 'receive_count_missing').length, 1);
  });

  test('delete는 DeleteMessage', async () => {
    const { sqs } = await run({ receives: [{ Messages: [message()] }], handle: async () => 'delete' });
    assert.deepEqual(sqs.ofType(DeleteMessageCommand), [{ QueueUrl: QUEUE, ReceiptHandle: 'rh-1' }]);
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 0);
  });

  test('retry는 아무 호출도 하지 않는다', async () => {
    const { sqs } = await run({ receives: [{ Messages: [message()] }], handle: async () => 'retry' });
    assert.equal(sqs.ofType(DeleteMessageCommand).length, 0);
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 0);
  });

  test('release는 ChangeMessageVisibility(0)', async () => {
    const { sqs } = await run({ receives: [{ Messages: [message()] }], handle: async () => 'release' });
    assert.deepEqual(sqs.ofType(ChangeMessageVisibilityCommand), [{ QueueUrl: QUEUE, ReceiptHandle: 'rh-1', VisibilityTimeout: 0 }]);
    assert.equal(sqs.ofType(DeleteMessageCommand).length, 0);
  });

  test('DeleteMessage·반납 실패는 로그만 남기고 루프가 계속된다', async () => {
    const del = await run({
      receives: [{ Messages: [message()] }, { Messages: [message({ ReceiptHandle: 'rh-2' })] }],
      failures: { DeleteMessageCommand: new Error('boom') },
    });
    assert.equal(del.sqs.ofType(DeleteMessageCommand).length, 2);
    assert.equal(del.logs.filter((l) => l.event === 'delete_failed' && l.level === 'error').length, 2);

    const rel = await run({ receives: [{ Messages: [message()] }], handle: async () => 'release', failures: { ChangeMessageVisibilityCommand: new Error('boom') } });
    assert.equal(rel.logs.filter((l) => l.event === 'release_failed').length, 1);
  });

  test('처리기가 던지면 남기고 루프가 계속된다', async () => {
    const { sqs, logs } = await run({
      receives: [{ Messages: [message()] }, {}],
      handle: async () => {
        throw new Error('bug');
      },
    });
    assert.equal(sqs.ofType(DeleteMessageCommand).length, 0);
    assert.equal(logs.filter((l) => l.event === 'handler_crashed').length, 1);
  });

  test('수신 실패는 1초부터 두 배씩 쉬고 성공하면 처음으로 돌아간다', async () => {
    const waits = [];
    const fail = () => new Error('network');
    const { logs } = await run({
      receives: [fail(), fail(), fail(), {}, fail()],
      extra: { sleep: async (ms) => void waits.push(ms) },
    });
    assert.deepEqual(waits, [1000, 2000, 4000, 1000]);
    assert.deepEqual(logs.filter((l) => l.event === 'receive_failed').map((l) => l.backoffMs), [1000, 2000, 4000, 1000]);
  });

  test('대기 시간은 60초에서 멈춘다', async () => {
    const waits = [];
    await run({
      receives: Array.from({ length: 9 }, () => new Error('network')),
      extra: { sleep: async (ms) => void waits.push(ms) },
    });
    assert.equal(Math.max(...waits), 60_000);
    assert.deepEqual(waits.slice(-3), [60_000, 60_000, 60_000]);
  });
});

describe('숨김 시간 연장', () => {
  test('주기마다 설정한 값으로 연장하고 작업이 끝나면 멈춘다', async (t) => {
    t.mock.timers.enable({ apis: ['setInterval'] });
    const controller = new AbortController();
    const finish = Promise.withResolvers();
    const sqs = fakeSqs({ receives: [{ Messages: [message()] }], controller });
    const { log } = recorder();
    const loop = runLoop({
      sqs,
      queueUrl: QUEUE,
      handle: () => finish.promise,
      signal: controller.signal,
      log,
      heartbeatIntervalMs: 60_000,
      visibilityExtensionSeconds: 180,
      sleep: async () => {},
    });
    await tick();
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 0);
    t.mock.timers.tick(59_999);
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 0);
    t.mock.timers.tick(1);
    t.mock.timers.tick(60_000);
    assert.deepEqual(sqs.ofType(ChangeMessageVisibilityCommand), [
      { QueueUrl: QUEUE, ReceiptHandle: 'rh-1', VisibilityTimeout: 180 },
      { QueueUrl: QUEUE, ReceiptHandle: 'rh-1', VisibilityTimeout: 180 },
    ]);
    finish.resolve('delete');
    await loop;
    t.mock.timers.tick(600_000);
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 2);
  });

  test('진행 중인 연장이 끝난 뒤에 반납한다', async () => {
    const controller = new AbortController();
    const order = [];
    const slow = Promise.withResolvers();
    const sqs = {
      async send(command) {
        if (command instanceof ReceiveMessageCommand) {
          if (order.length > 0) throw Object.assign(new Error('aborted'), { name: 'AbortError' });
          return { Messages: [message()] };
        }
        const seconds = command.input.VisibilityTimeout;
        order.push(`start:${seconds}`);
        if (seconds === 180) await slow.promise;
        order.push(`end:${seconds}`);
        return {};
      },
    };
    const finish = Promise.withResolvers();
    const loop = runLoop({
      sqs, queueUrl: QUEUE, handle: () => finish.promise, signal: controller.signal, log: recorder().log,
      heartbeatIntervalMs: 10, visibilityExtensionSeconds: 180, sleep: async () => {},
    });
    await new Promise((resolve) => setTimeout(resolve, 40));
    controller.abort();
    finish.resolve('release');
    await tick();
    assert.ok(!order.includes('start:0'), '연장이 끝나기 전에 반납했습니다.');
    slow.resolve();
    await loop;
    assert.ok(order.indexOf('start:0') > order.lastIndexOf('start:180'), '반납 뒤에 연장이 시작됐습니다.');
    assert.ok(order.indexOf('start:0') > order.lastIndexOf('end:180'), '연장이 끝나기 전에 반납했습니다.');
  });

  test('연장 실패는 경고 로그만 남기고 작업은 계속된다', async () => {
    const finish = Promise.withResolvers();
    const controller = new AbortController();
    const sqs = fakeSqs({ receives: [{ Messages: [message()] }], controller, failures: { ChangeMessageVisibilityCommand: new Error('throttled') } });
    const { logs, log } = recorder();
    const loop = runLoop({
      sqs, queueUrl: QUEUE, handle: () => finish.promise, signal: controller.signal, log,
      heartbeatIntervalMs: 10, visibilityExtensionSeconds: 180, sleep: async () => {},
    });
    await new Promise((resolve) => setTimeout(resolve, 60));
    finish.resolve('delete');
    await loop;
    assert.ok(logs.some((l) => l.event === 'heartbeat_failed' && l.level === 'warn'));
    assert.equal(sqs.ofType(DeleteMessageCommand).length, 1);
  });
});

describe('종료 신호', () => {
  test('대기 중인 수신을 끊고 루프가 끝난다', async () => {
    const controller = new AbortController();
    const sqs = {
      send: (_command, { abortSignal }) =>
        new Promise((_resolve, reject) => abortSignal.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })))),
    };
    const { logs, log } = recorder();
    const loop = runLoop({ sqs, queueUrl: QUEUE, handle: async () => 'delete', signal: controller.signal, log });
    await tick();
    controller.abort();
    await loop;
    assert.equal(logs.length, 0); // 종료 때문에 끊긴 수신은 오류 로그가 아니다
  });

  test('오류 대기 중 신호가 오면 바로 끝난다', async () => {
    const controller = new AbortController();
    const sqs = { send: async () => { throw new Error('network'); } };
    const { log } = recorder();
    const loop = runLoop({ sqs, queueUrl: QUEUE, handle: async () => 'delete', signal: controller.signal, log });
    await tick();
    controller.abort();
    await loop; // 실제 sleep(1초)이 신호로 끊기지 않으면 시험이 제한 시간에 걸린다
  });

  test('작업 중이면 처리기 신호가 끊기고 release가 불린다', async () => {
    const controller = new AbortController();
    const sqs = fakeSqs({ receives: [{ Messages: [message()] }], controller });
    const { log } = recorder();
    let handlerSignal;
    const handle = (_body, { signal }) =>
      new Promise((resolve) => {
        handlerSignal = signal;
        signal.addEventListener('abort', () => resolve('release'));
      });
    const loop = runLoop({ sqs, queueUrl: QUEUE, handle, signal: controller.signal, log, sleep: async () => {} });
    await tick();
    controller.abort();
    await loop;
    assert.equal(handlerSignal.aborted, true);
    assert.deepEqual(sqs.ofType(ChangeMessageVisibilityCommand), [{ QueueUrl: QUEUE, ReceiptHandle: 'rh-1', VisibilityTimeout: 0 }]);
    assert.equal(sqs.ofType(DeleteMessageCommand).length, 0);
  });

  test('종료 중 retry 결과도 반납하고 message_released를 남긴다', async () => {
    const controller = new AbortController();
    const sqs = fakeSqs({ receives: [{ Messages: [message()] }], controller });
    const { logs, log } = recorder();
    const handle = async () => (controller.abort(), 'retry');
    await runLoop({ sqs, queueUrl: QUEUE, handle, signal: controller.signal, log });
    assert.equal(sqs.ofType(ChangeMessageVisibilityCommand).length, 1);
    assert.deepEqual(logs.filter((l) => l.event === 'message_released').map(({ level, messageId, reason }) => [level, messageId, reason]), [['info', 'm-1', 'shutdown']]);
  });

  test('이미 종료 신호가 있으면 수신하지 않는다', async () => {
    const controller = new AbortController();
    controller.abort();
    const sqs = fakeSqs({ receives: [], controller });
    await runLoop({ sqs, queueUrl: QUEUE, handle: async () => 'delete', signal: controller.signal, log: recorder().log });
    assert.equal(sqs.sent.length, 0);
  });
});
