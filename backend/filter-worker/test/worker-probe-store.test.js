// 견본 내려받기(스트림 → 파일, 크기 상한, 없는 키)와 레시피 쓰기 입력
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { access, mkdtemp, readFile, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Readable } from 'node:stream';
import { GetObjectCommand, PutObjectCommand } from '@aws-sdk/client-s3';
import { downloadProbe, putRecipe } from '../src/worker/probe-store.js';

const exists = (path) => access(path).then(() => true, () => false);

// 명령을 기록하고 정해진 응답(또는 오류)을 돌려주는 가짜 S3
function fakeS3(respond) {
  return {
    commands: [],
    async send(command, options) {
      this.commands.push({ command, options });
      const result = respond(command);
      if (result instanceof Error) throw result;
      return result;
    },
  };
}
const awsError = (name) => Object.assign(new Error(name), { name });

describe('downloadProbe', () => {
  let dir;
  before(async () => {
    dir = await mkdtemp(join(tmpdir(), 'probe-store-'));
  });
  after(() => rm(dir, { recursive: true, force: true }));

  test('본문을 파일로 쓰고 요청 입력을 확인한다', async () => {
    const s3 = fakeS3(() => ({ Body: Readable.from([Buffer.from('abc'), Buffer.from('def')]), ContentLength: 6 }));
    const dest = join(dir, 'ok.jpg');
    const result = await downloadProbe(s3, { bucket: 'b', key: 'k/p.jpg', maxBytes: 100, dest });
    assert.deepEqual(result, { kind: 'ok', bytes: 6 });
    assert.equal(await readFile(dest, 'utf8'), 'abcdef');
    const [{ command }] = s3.commands;
    assert.ok(command instanceof GetObjectCommand);
    assert.deepEqual(command.input, { Bucket: 'b', Key: 'k/p.jpg' });
  });

  test('상한을 넘으면 중간에 끊고 too-large, 부분 파일이 없다', async () => {
    // ContentLength를 알려 주지 않는 응답(청크로 넘침)
    const s3 = fakeS3(() => ({ Body: Readable.from([Buffer.alloc(6), Buffer.alloc(6), Buffer.alloc(6)]) }));
    const dest = join(dir, 'big.jpg');
    assert.deepEqual(await downloadProbe(s3, { bucket: 'b', key: 'k', maxBytes: 10, dest }), { kind: 'too-large' });
    assert.equal(await exists(dest), false);
  });

  test('ContentLength가 상한보다 크면 본문을 읽지 않고 too-large', async () => {
    const body = Readable.from([Buffer.alloc(1)]);
    const s3 = fakeS3(() => ({ Body: body, ContentLength: 11 }));
    const dest = join(dir, 'declared.jpg');
    assert.deepEqual(await downloadProbe(s3, { bucket: 'b', key: 'k', maxBytes: 10, dest }), { kind: 'too-large' });
    assert.equal(await exists(dest), false);
    assert.equal(body.destroyed, true);
  });

  test('NoSuchKey -> missing, AccessDenied·그 밖 -> throw', async () => {
    const dest = join(dir, 'x.jpg');
    assert.deepEqual(await downloadProbe(fakeS3(() => awsError('NoSuchKey')), { bucket: 'b', key: 'k', maxBytes: 10, dest }), { kind: 'missing' });
    await assert.rejects(downloadProbe(fakeS3(() => awsError('AccessDenied')), { bucket: 'b', key: 'k', maxBytes: 10, dest }), { name: 'AccessDenied' });
    await assert.rejects(downloadProbe(fakeS3(() => awsError('InternalError')), { bucket: 'b', key: 'k', maxBytes: 10, dest }), { name: 'InternalError' });
  });

  test('본문 읽기 중 오류는 throw하고 부분 파일을 지운다', async () => {
    const body = new Readable({
      read() {
        this.push(Buffer.from('조각'));
        this.destroy(new Error('연결 끊김'));
      },
    });
    const dest = join(dir, 'broken.jpg');
    await assert.rejects(downloadProbe(fakeS3(() => ({ Body: body })), { bucket: 'b', key: 'k', maxBytes: 100, dest }), /연결 끊김/);
    assert.equal(await exists(dest), false);
  });
});

describe('putRecipe', () => {
  test('Bucket, Key, Body, ContentType, CacheControl', async () => {
    const s3 = fakeS3(() => ({}));
    const body = Buffer.from('{"schemaVersion":1}');
    await putRecipe(s3, { bucket: 'b', key: 'chalkak/filters/dev/recipes/id.json', body });
    const [{ command }] = s3.commands;
    assert.ok(command instanceof PutObjectCommand);
    assert.deepEqual(command.input, {
      Bucket: 'b',
      Key: 'chalkak/filters/dev/recipes/id.json',
      Body: body,
      ContentType: 'application/json',
      CacheControl: 'public, max-age=31536000, immutable',
    });
  });

  test('S3 오류는 그대로 던진다', async () => {
    await assert.rejects(putRecipe(fakeS3(() => awsError('AccessDenied')), { bucket: 'b', key: 'k', body: Buffer.alloc(0) }), { name: 'AccessDenied' });
  });
});
