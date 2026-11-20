// S3 이벤트 해석: 2장 표의 모든 갈래가 probe 또는 ignore(reason)로 나뉘는지 본다
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { parseProbeEvent } from '../src/worker/s3-event.js';

const OPTIONS = { bucket: 'techcourse-project-2026', prefix: 'chalkak', environments: ['dev', 'prod'] };
const PROBE_ID = '0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f';
const KEY = `chalkak/filter-probes/dev/${PROBE_ID}.jpg`;

function body({ key = KEY, bucket = OPTIONS.bucket, size = 8123456, omitSize = false, eventName = 'ObjectCreated:Put', eventSource = 'aws:s3' } = {}) {
  const object = { key, eTag: 'etag', sequencer: 'seq' };
  if (!omitSize) object.size = size;
  return JSON.stringify({
    Records: [{ eventVersion: '2.1', eventSource, awsRegion: 'ap-northeast-2', eventName, s3: { bucket: { name: bucket }, object } }],
  });
}

const reasonOf = (text) => {
  const result = parseProbeEvent(text, OPTIONS);
  assert.equal(result.kind, 'ignore');
  return result.reason;
};

describe('S3 이벤트 해석', () => {
  test('정상 레코드는 dev와 prod 모두 probe로 해석한다', () => {
    assert.deepEqual(parseProbeEvent(body(), OPTIONS), { kind: 'probe', env: 'dev', probeId: PROBE_ID, key: KEY, size: 8123456 });
    const prodKey = `chalkak/filter-probes/prod/${PROBE_ID}.jpg`;
    assert.deepEqual(parseProbeEvent(body({ key: prodKey }), OPTIONS), {
      kind: 'probe', env: 'prod', probeId: PROBE_ID, key: prodKey, size: 8123456,
    });
  });

  test('키의 +와 %xx를 디코드한 뒤 판정하고, 잘못된 퍼센트 인코딩은 invalid_key_encoding', () => {
    const encoded = 'chalkak%2Ffilter-probes%2Fdev%2F0190f5c2-7b4a-7c3e-9d2f-1a2b3c4d5e6f.jpg';
    const result = parseProbeEvent(body({ key: encoded }), OPTIONS);
    assert.equal(result.kind, 'probe');
    assert.equal(result.key, KEY);
    // +는 공백으로 풀리므로 모양이 맞지 않는다(unquote_plus와 같은 규칙)
    const plus = parseProbeEvent(body({ key: 'chalkak/filter-probes/dev/a+b.jpg' }), OPTIONS);
    assert.equal(plus.reason, 'unexpected_key');
    assert.equal(plus.key, 'chalkak/filter-probes/dev/a b.jpg');
    assert.equal(reasonOf(body({ key: 'chalkak/filter-probes/dev/%E0%A4%A.jpg' })), 'invalid_key_encoding');
  });

  test('s3:TestEvent는 test_event', () => {
    assert.equal(reasonOf(JSON.stringify({ Service: 'Amazon S3', Event: 's3:TestEvent', Time: 'now', Bucket: OPTIONS.bucket })), 'test_event');
  });

  test('JSON이 아니거나 배열이거나 Records가 없거나 2건이면 각 reason', () => {
    assert.equal(reasonOf('not json'), 'invalid_json');
    assert.equal(reasonOf('[]'), 'invalid_json');
    assert.equal(reasonOf('null'), 'invalid_json');
    assert.equal(reasonOf('{}'), 'unexpected_record_count');
    assert.equal(reasonOf('{"Records":[]}'), 'unexpected_record_count');
    const record = JSON.parse(body()).Records[0];
    assert.equal(reasonOf(JSON.stringify({ Records: [record, record] })), 'unexpected_record_count');
  });

  test('ObjectCreated가 아니거나 eventSource가 다르면 not_object_created', () => {
    assert.equal(reasonOf(body({ eventName: 'ObjectRemoved:Delete' })), 'not_object_created');
    assert.equal(reasonOf(body({ eventSource: 'aws:sns' })), 'not_object_created');
    assert.equal(parseProbeEvent(body({ eventName: 'ObjectCreated:CompleteMultipartUpload' }), OPTIONS).kind, 'probe');
  });

  test('다른 버킷은 unexpected_bucket', () => {
    assert.equal(reasonOf(body({ bucket: 'other-bucket' })), 'unexpected_bucket');
  });

  test('대문자 UUID, .jpeg, 하위 경로, 다른 접두사는 unexpected_key', () => {
    for (const key of [
      `chalkak/filter-probes/dev/${PROBE_ID.toUpperCase()}.jpg`,
      `chalkak/filter-probes/dev/${PROBE_ID}.jpeg`,
      `chalkak/filter-probes/dev/sub/${PROBE_ID}.jpg`,
      `other/filter-probes/dev/${PROBE_ID}.jpg`,
      `chalkak/filter-probes/dev/not-a-uuid.jpg`,
    ]) {
      const result = parseProbeEvent(body({ key }), OPTIONS);
      assert.equal(result.reason, 'unexpected_key', key);
      assert.equal(result.key, key);
    }
  });

  test('설정에 없는 환경은 unsupported_environment', () => {
    assert.equal(reasonOf(body({ key: `chalkak/filter-probes/staging/${PROBE_ID}.jpg` })), 'unsupported_environment');
  });

  test('size가 음수·소수·문자열이면 invalid_size, 없으면 null', () => {
    assert.equal(reasonOf(body({ size: -1 })), 'invalid_size');
    assert.equal(reasonOf(body({ size: 1.5 })), 'invalid_size');
    assert.equal(reasonOf(body({ size: '100' })), 'invalid_size');
    assert.equal(parseProbeEvent(body({ omitSize: true }), OPTIONS).size, null);
    assert.equal(parseProbeEvent(body({ size: 0 }), OPTIONS).size, 0);
  });

  test('접두사의 정규식 특수문자는 글자 그대로 비교한다', () => {
    const options = { ...OPTIONS, prefix: 'a.b' };
    assert.equal(parseProbeEvent(body({ key: `a.b/filter-probes/dev/${PROBE_ID}.jpg` }), options).kind, 'probe');
    assert.equal(parseProbeEvent(body({ key: `axb/filter-probes/dev/${PROBE_ID}.jpg` }), options).reason, 'unexpected_key');
  });
});
