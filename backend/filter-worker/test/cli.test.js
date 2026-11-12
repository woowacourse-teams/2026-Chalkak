// 추출 CLI와 최상위 API의 실패 경로: 큰 견본을 돌리지 않고도 닿는 곳(바이트 검사, XMP, 오류 변환)
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { extractRecipe } from '../src/extract-recipe.js';
import { ExtractionFailure, failureLines } from '../src/failure.js';
import { H, W, inputProbe } from '../src/m1/layout.js';

const CLI = fileURLToPath(new URL('../src/cli.js', import.meta.url));
const run = (...args) => spawnSync(process.execPath, [CLI, ...args], { encoding: 'utf8' });

describe('추출 CLI', () => {
  let dir, textFile, plainJpeg;
  before(async () => {
    dir = await mkdtemp(join(tmpdir(), 'extract-cli-'));
    textFile = join(dir, 'not-a-jpeg.txt');
    plainJpeg = join(dir, 'plain.jpg');
    await writeFile(textFile, 'hello');
    await writeFile(plainJpeg, await sharp({ create: { width: 8, height: 8, channels: 3, background: '#888' } }).jpeg().toBuffer());
  });
  after(() => rm(dir, { recursive: true, force: true }));

  test('JPG가 아니면 종료 코드 2와 stderr JSON', () => {
    const r = run(textFile);
    assert.equal(r.status, 2);
    assert.deepEqual(JSON.parse(r.stderr), { type: 'INVALID_PROBE', message: 'JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.' });
    assert.equal(r.stderr.trim().split('\n').length, 1); // 작가 탓이면 원인 줄이 없다
    assert.equal(r.stdout, '');
  });

  test('XMP가 없는 JPG도 종료 코드 2 (디코드 전에 실패)', () => {
    const r = run(plainJpeg);
    assert.equal(r.status, 2);
    assert.equal(JSON.parse(r.stderr).type, 'INVALID_PROBE');
  });

  test('파일이 없으면 종료 코드 2', () => {
    const r = run(join(dir, 'missing.jpg'));
    assert.equal(r.status, 2);
    assert.equal(JSON.parse(r.stderr).type, 'INVALID_PROBE');
  });

  test('예상 밖 오류는 고정 안내 문구와 PROCESSING_FAILED, 원인은 둘째 줄', async () => {
    // 편집 없는 견본 + 보정 값이 모두 0인 XMP: 추출은 끝까지 돌고, 쓰기 단계(없는 폴더)에서 실패한다
    const raw = Buffer.from(inputProbe().buffer);
    const xmp = '<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/" crs:Exposure2012="0"/></rdf:RDF></x:xmpmeta>';
    const jpeg = join(dir, 'probe.jpg');
    await writeFile(jpeg, await sharp(raw, { raw: { width: W, height: H, channels: 4 } }).removeAlpha().withXmp(xmp).jpeg({ quality: 100 }).toBuffer());
    const r = run(jpeg, '--out', join(dir, 'no-such-dir', 'recipe.json'));
    assert.equal(r.status, 1);
    const [first, second] = r.stderr.trim().split('\n');
    assert.deepEqual(JSON.parse(first), { type: 'PROCESSING_FAILED', message: '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.' });
    assert.match(second, /^cause: /);
  });

  test('인자가 없으면 종료 코드 1', () => {
    const r = run();
    assert.equal(r.status, 1);
    assert.equal(JSON.parse(r.stderr).type, 'PROCESSING_FAILED');
  });
});

describe('운영자용 stderr 줄', () => {
  test('cause가 없으면 JSON 한 줄, 있으면 JSON(type·message만) 다음 줄에 cause', () => {
    assert.equal(failureLines(ExtractionFailure.invalidProbe('나빠요')).length, 1);
    const failure = ExtractionFailure.processingFailed('다시 올려 주세요.');
    failure.cause = new RangeError('내부 원인');
    const [json, cause, ...rest] = failureLines(failure);
    assert.deepEqual(JSON.parse(json), { type: 'PROCESSING_FAILED', message: '다시 올려 주세요.' });
    assert.match(cause, /^cause: RangeError: 내부 원인/);
    assert.ok(rest.every((line) => !line.startsWith('{')));
    failure.cause = '문자열 원인';
    assert.equal(failureLines(failure)[1], 'cause: 문자열 원인');
  });
});

describe('extractRecipe 오류 변환', () => {
  test('ExtractionFailure는 그대로 던진다', async () => {
    await assert.rejects(extractRecipe(Buffer.from('hello')), { type: 'INVALID_PROBE' });
  });

  test('그 밖의 오류는 PROCESSING_FAILED로 바꾸고 원인을 cause에 둔다', async () => {
    const cause = new RangeError('예상 밖 오류');
    const hostile = { get length() { throw cause; } };
    await assert.rejects(extractRecipe(hostile), (error) => {
      assert.ok(error instanceof ExtractionFailure);
      assert.equal(error.type, 'PROCESSING_FAILED');
      assert.equal(error.message, '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.');
      assert.equal(error.cause, cause);
      return true;
    });
  });
});
