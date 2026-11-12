// S3 → SQS 직접 알림 본문을 견본 처리 대상으로 해석한다. 믿을 수 있는 probeId가 없으면 ignore로 돌려
// 호출한 쪽이 로그만 남기고 콜백 없이 메시지를 지우게 한다. SNS 감싸기는 처리하지 않는다.

const UUID_PATTERN = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';

const escapeRegExp = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const ignore = (reason, key) => (key === undefined ? { kind: 'ignore', reason } : { kind: 'ignore', reason, key });

const isPlainObject = (value) => value !== null && typeof value === 'object' && !Array.isArray(value);

export function parseProbeEvent(body, { bucket, prefix, environments }) {
  let event;
  try {
    event = JSON.parse(body);
  } catch {
    return ignore('invalid_json');
  }
  if (!isPlainObject(event)) {
    return ignore('invalid_json');
  }
  // S3가 알림을 처음 저장할 때 보내는 시험 이벤트
  if (event.Event === 's3:TestEvent') {
    return ignore('test_event');
  }
  // S3 직접 알림은 메시지당 레코드가 하나다
  if (!Array.isArray(event.Records) || event.Records.length !== 1) {
    return ignore('unexpected_record_count');
  }

  const record = event.Records[0];
  if (
    !isPlainObject(record) ||
    record.eventSource !== 'aws:s3' ||
    typeof record.eventName !== 'string' ||
    !record.eventName.startsWith('ObjectCreated:')
  ) {
    return ignore('not_object_created');
  }

  const s3 = isPlainObject(record.s3) ? record.s3 : {};
  if (!isPlainObject(s3.bucket) || s3.bucket.name !== bucket) {
    return ignore('unexpected_bucket');
  }

  const object = isPlainObject(s3.object) ? s3.object : {};
  if (typeof object.key !== 'string') {
    return ignore('unexpected_key');
  }
  // S3 이벤트의 키는 URL 인코딩돼 있다(공백은 +). 파이썬 unquote_plus와 같은 규칙
  let key;
  try {
    key = decodeURIComponent(object.key.replaceAll('+', ' '));
  } catch {
    return ignore('invalid_key_encoding');
  }

  // 백엔드가 소문자 정규 UUID로 서명 경로를 만들고 발급하는 키도 소문자이므로 소문자만 받는다
  const keyPattern = new RegExp(`^${escapeRegExp(prefix)}/filter-probes/(?<env>[^/]+)/(?<probeId>${UUID_PATTERN})\\.jpg$`);
  const match = keyPattern.exec(key);
  if (!match) {
    return ignore('unexpected_key', key);
  }
  const { env, probeId } = match.groups;
  if (!environments.includes(env)) {
    return ignore('unsupported_environment', key);
  }

  let size = null;
  if (object.size !== undefined && object.size !== null) {
    if (!Number.isInteger(object.size) || object.size < 0) {
      return ignore('invalid_size', key);
    }
    size = object.size;
  }

  return { kind: 'probe', env, probeId, key, size };
}
