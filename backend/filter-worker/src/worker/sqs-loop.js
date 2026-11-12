// SQS 긴 폴링 루프(README 설계 3-2·3-4절). 메시지를 한 건씩 받아 job의 handleMessage에 맡기고,
// 작업이 도는 동안 숨김 시간을 연장하며, 결과('delete'|'retry'|'release')를 SQS에 반영한다.
// 종료 신호(signal.abort)가 오면 받는 중인 수신을 끊고, 진행 중 작업의 신호도 끊어 반납(release)하게 한 뒤 끝난다.
import { setTimeout as delay } from 'node:timers/promises';
import { ChangeMessageVisibilityCommand, DeleteMessageCommand, ReceiveMessageCommand } from '@aws-sdk/client-sqs';

export const WAIT_TIME_SECONDS = 20;
export const DEFAULT_HEARTBEAT_INTERVAL_MS = 60_000;
export const DEFAULT_VISIBILITY_EXTENSION_SECONDS = 180;
export const INITIAL_BACKOFF_MS = 1000;
export const MAX_BACKOFF_MS = 60_000;

const describeError = (error) => `${error?.name ?? 'Error'}: ${error?.message ?? String(error)}`;

/**
 * @param {{
 *   sqs: { send(command, options?): Promise<any> },
 *   queueUrl: string,
 *   handle: (body: string, context: { messageId: string, receiveCount: number, signal: AbortSignal }) => Promise<'delete'|'retry'|'release'>,
 *   signal: AbortSignal,
 *   log: { info: Function, warn: Function, error: Function },
 *   heartbeatIntervalMs?: number,
 *   visibilityExtensionSeconds?: number,
 *   sleep?: (ms: number, signal: AbortSignal) => Promise<void>,
 * }} options
 */
export async function runLoop({
  sqs,
  queueUrl,
  handle,
  signal,
  log,
  heartbeatIntervalMs = DEFAULT_HEARTBEAT_INTERVAL_MS,
  visibilityExtensionSeconds = DEFAULT_VISIBILITY_EXTENSION_SECONDS,
  sleep = (ms, abortSignal) => delay(ms, undefined, { signal: abortSignal }),
}) {
  const changeVisibility = (receiptHandle, seconds) =>
    sqs.send(new ChangeMessageVisibilityCommand({ QueueUrl: queueUrl, ReceiptHandle: receiptHandle, VisibilityTimeout: seconds }));

  async function processMessage(message) {
    const receiptHandle = message.ReceiptHandle;
    const messageId = message.MessageId;
    let receiveCount = Number.parseInt(message.Attributes?.ApproximateReceiveCount, 10);
    if (!Number.isInteger(receiveCount) || receiveCount < 1) {
      receiveCount = 1;
      log.error('receive_count_missing', { messageId });
    }

    // 작업이 끝날 때까지 주기마다 숨김 시간을 늘린다. 실패해도 작업은 계속한다
    // 진행 중인 연장 요청은 inFlight에 두고, 작업이 끝나면 기다린 뒤에 삭제·반납한다(연장이 반납을 덮어쓰지 않게)
    let inFlight = Promise.resolve();
    let finished = false;
    const heartbeat = setInterval(() => {
      if (finished) return;
      const request = changeVisibility(receiptHandle, visibilityExtensionSeconds).catch((error) => {
        log.warn('heartbeat_failed', { messageId, error: describeError(error) });
      });
      inFlight = Promise.all([inFlight, request]);
    }, heartbeatIntervalMs);

    let outcome;
    try {
      outcome = await handle(message.Body ?? '', { messageId, receiveCount, signal });
    } catch (error) {
      // handleMessage는 예상 밖 오류를 스스로 결과로 바꾼다. 여기까지 온 것은 버그이므로 남겨 두고 수신 횟수로 정리한다
      log.error('handler_crashed', { messageId, error: describeError(error) });
      outcome = 'retry';
    } finally {
      finished = true;
      clearInterval(heartbeat);
    }
    await inFlight;
    // 종료 중에 남기기로 한 메시지는 바로 다시 받게 반납한다(숨김 시간 동안 묶어 두지 않는다)
    if (outcome === 'retry' && signal.aborted) {
      outcome = 'release';
      log.info('message_released', { messageId, receiveCount, reason: 'shutdown' });
    }

    if (outcome === 'delete') {
      try {
        await sqs.send(new DeleteMessageCommand({ QueueUrl: queueUrl, ReceiptHandle: receiptHandle }));
      } catch (error) {
        log.error('delete_failed', { messageId, error: describeError(error) });
      }
    } else if (outcome === 'release') {
      try {
        await changeVisibility(receiptHandle, 0);
      } catch (error) {
        log.warn('release_failed', { messageId, error: describeError(error) });
      }
    }
  }

  let backoffMs = INITIAL_BACKOFF_MS;
  while (!signal.aborted) {
    let response;
    try {
      response = await sqs.send(
        new ReceiveMessageCommand({
          QueueUrl: queueUrl,
          MaxNumberOfMessages: 1,
          WaitTimeSeconds: WAIT_TIME_SECONDS,
          MessageSystemAttributeNames: ['ApproximateReceiveCount'],
        }),
        { abortSignal: signal },
      );
      backoffMs = INITIAL_BACKOFF_MS;
    } catch (error) {
      if (signal.aborted) break;
      log.warn('receive_failed', { error: describeError(error), backoffMs });
      try {
        await sleep(backoffMs, signal);
      } catch {
        break; // 대기 중 종료 신호
      }
      backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
      continue;
    }

    const message = response?.Messages?.[0];
    if (message) await processMessage(message);
  }
}
