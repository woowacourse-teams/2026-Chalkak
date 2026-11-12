import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLogger } from '../src/worker/log.js';

test('한 줄짜리 JSON에 time·level·event가 들어가고 공통 필드가 우선한다', () => {
  const written = [];
  const log = createLogger({ write: (line) => written.push(line), now: () => new Date('2026-11-12T00:00:00Z') });
  log.warn('receive_failed', { backoffMs: 1000, level: 'x' });
  assert.equal(written.length, 1);
  assert.ok(written[0].endsWith('\n') && !written[0].slice(0, -1).includes('\n'));
  assert.deepEqual(JSON.parse(written[0]), { backoffMs: 1000, time: '2026-11-12T00:00:00.000Z', level: 'warn', event: 'receive_failed' });
});

test('직렬화할 수 없는 필드여도 던지지 않는다', () => {
  const written = [];
  const log = createLogger({ write: (line) => written.push(line) });
  const circular = {};
  circular.self = circular;
  log.info('x', circular);
  assert.equal(JSON.parse(written[0]).logError, 'fields_not_serializable');
});
