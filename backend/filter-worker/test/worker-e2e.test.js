// 루프 + 실제 처리 단계 + 실제 콜백 클라이언트 + 모의 백엔드(서명 검증) + 가짜 SQS + 메모리 S3 + 가짜 자식
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import { fileURLToPath } from 'node:url';
import { GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { DeleteMessageCommand, ReceiveMessageCommand } from '@aws-sdk/client-sqs';
import { createCallbackClient } from '../src/worker/callback-client.js';
import { runExtraction } from '../src/worker/extract-runner.js';
import { createJobHandler } from '../src/worker/job.js';
import { createLogger } from '../src/worker/log.js';
import { runLoop } from '../src/worker/sqs-loop.js';
import { startMockBackend } from './support/mock-backend.js';

const FAKE_CHILD = fileURLToPath(new URL('./support/fake-extract-child.js', import.meta.url));
const SECRET = 'chalkak-filter-worker-test-secret-0123456789';
const PROBE_ID = '0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f';
const BUCKET = 'techcourse-project-2026';
const QUEUE = 'https://sqs.ap-northeast-2.amazonaws.com/123456789012/chalkak-filter-extraction';
const KEY = `chalkak/filter-probes/dev/${PROBE_ID}.jpg`;

test('메시지 하나가 start → 레시피 쓰기 → complete → 삭제로 끝난다', async () => {
  const workDir = await mkdtemp(join(tmpdir(), 'worker-e2e-'));
  const backend = await startMockBackend({ secret: SECRET, environment: 'dev' });
  const controller = new AbortController();
  const puts = [];
  const s3 = {
    async send(command) {
      if (command instanceof GetObjectCommand) {
        assert.equal(command.input.Key, KEY);
        const body = Buffer.from(JSON.stringify({ mode: 'ok' }));
        return { Body: Readable.from([body]), ContentLength: body.length };
      }
      assert.ok(command instanceof PutObjectCommand);
      puts.push(command.input);
      return {};
    },
  };
  const sent = [];
  let delivered = false;
  const sqs = {
    async send(command) {
      sent.push(command);
      if (command instanceof ReceiveMessageCommand) {
        if (delivered) {
          controller.abort();
          throw Object.assign(new Error('aborted'), { name: 'AbortError' });
        }
        delivered = true;
        const body = JSON.stringify({
          Records: [{ eventSource: 'aws:s3', eventName: 'ObjectCreated:Put', s3: { bucket: { name: BUCKET }, object: { key: KEY, size: 100 } } }],
        });
        return { Messages: [{ MessageId: 'm-1', ReceiptHandle: 'rh-1', Body: body, Attributes: { ApproximateReceiveCount: '1' } }] };
      }
      return {};
    },
  };
  const lines = [];
  const log = createLogger({ write: (line) => lines.push(JSON.parse(line)) });
  try {
    const callbacks = createCallbackClient({ baseUrls: { dev: backend.baseUrl, prod: backend.baseUrl }, secret: SECRET });
    const handle = createJobHandler({
      config: { bucket: BUCKET, prefix: 'chalkak', environments: ['dev', 'prod'], workDir, extractionTimeoutMs: 30_000 },
      s3,
      callbacks,
      runExtraction: (options) => runExtraction({ ...options, childPath: FAKE_CHILD }),
      log,
    });
    await runLoop({ sqs, queueUrl: QUEUE, handle, signal: controller.signal, log });

    assert.deepEqual(backend.requests.map((r) => [r.action, r.probeId, r.signatureValid]), [
      ['start', PROBE_ID, true],
      ['complete', PROBE_ID, true],
    ]);
    const recipeId = backend.recipeIds.get(PROBE_ID);
    assert.equal(puts.length, 1);
    assert.equal(puts[0].Key, `chalkak/filters/dev/recipes/${recipeId}.json`);
    assert.deepEqual(sent.filter((c) => c instanceof DeleteMessageCommand).map((c) => c.input), [{ QueueUrl: QUEUE, ReceiptHandle: 'rh-1' }]);
    assert.ok(lines.some((l) => l.event === 'job_finished' && l.outcome === 'delete'));
    assert.ok(!JSON.stringify(lines).includes(SECRET));
  } finally {
    await backend.close();
    await rm(workDir, { recursive: true, force: true });
  }
});
