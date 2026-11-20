// 추출 자식 프로세스 실행기: 판정 6종, 자식 환경에 시크릿 없음, 실제 자식의 종료 규약
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { H, W, inputProbe } from '../src/m1/layout.js';
import { runExtraction } from '../src/worker/extract-runner.js';

const FAKE_CHILD = fileURLToPath(new URL('./support/fake-extract-child.js', import.meta.url));
const SECRET = 'chalkak-filter-worker-test-secret-0123456789';

const alive = (pid) => {
  try {
    process.kill(pid, 0);
    return true;
  } catch {
    return false;
  }
};

describe('추출 실행기', () => {
  let root;
  before(async () => {
    root = await mkdtemp(join(tmpdir(), 'extract-runner-'));
  });
  after(() => rm(root, { recursive: true, force: true }));

  let seq = 0;
  // probe 파일에 가짜 자식의 동작을 적고 실행한다
  async function runFake(spec, options = {}) {
    const dir = join(root, `case-${seq++}`);
    await rm(dir, { recursive: true, force: true });
    await mkdir(dir);
    const probePath = join(dir, 'probe.jpg');
    await writeFile(probePath, JSON.stringify(spec));
    const result = await runExtraction({ probePath, outDir: dir, childPath: FAKE_CHILD, ...options });
    const info = await readFile(join(dir, 'child-info.json'), 'utf8').then(JSON.parse, () => null);
    return { result, info, dir };
  }

  test('종료 0과 두 파일 -> ok (레시피는 파일 바이트 그대로)', async () => {
    const recipeText = '{"schemaVersion":1,"x":[1, 2]}';
    const { result } = await runFake({ mode: 'ok', recipeText, metaText: JSON.stringify({ extractorVersion: '1.0.0', probeVersion: 2, branch: 'G1', holdout: {}, warnings: ['w'], xmpSummary: {} }) });
    assert.equal(result.kind, 'ok');
    assert.equal(result.recipeBytes.toString('utf8'), recipeText);
    assert.equal(result.meta.branch, 'G1');
  });

  test('종료 0인데 결과 JSON이 이상하면 error', async () => {
    const { result } = await runFake({ mode: 'ok', recipeText: '{깨짐' });
    assert.equal(result.kind, 'error');
    assert.equal(result.exitCode, 0);
  });

  test('결과 파일이 상한(레시피 8MB, 메타 1MB)을 넘으면 error', async () => {
    for (const spec of [{ recipePad: 8 * 1024 * 1024 + 1 }, { metaPad: 1024 * 1024 + 1 }]) {
      const { result } = await runFake({ mode: 'ok', ...spec });
      assert.equal(result.kind, 'error');
      assert.match(result.cause, /상한/);
    }
    assert.equal((await runFake({ mode: 'ok', recipePad: 8 * 1024 * 1024 })).result.kind, 'ok');
  });

  test('meta 모양이 불완전하면 error', async () => {
    const valid = { extractorVersion: '1.0.0', probeVersion: 2, branch: 'A2', holdout: {}, warnings: [], xmpSummary: {} };
    const broken = [{ branch: 'A2' }, { ...valid, extractorVersion: '' }, { ...valid, probeVersion: 1.5 }, { ...valid, branch: 'X9' },
      { ...valid, warnings: [1] }, { ...valid, warnings: 'w' }, { ...valid, xmpSummary: [] }, { ...valid, holdout: null }, []];
    for (const meta of broken) {
      const { result } = await runFake({ mode: 'ok', metaText: JSON.stringify(meta) });
      assert.equal(result.kind, 'error', JSON.stringify(meta));
      assert.match(result.cause, /모양/);
    }
  });

  test('종료 2 + INVALID_PROBE JSON -> invalid(문구)', async () => {
    const stderr = `${JSON.stringify({ type: 'INVALID_PROBE', message: '나쁜 견본이에요.' })}\n`;
    const { result } = await runFake({ mode: 'exit', code: 2, stderr });
    assert.deepEqual({ kind: result.kind, message: result.message }, { kind: 'invalid', message: '나쁜 견본이에요.' });
  });

  test('종료 2인데 stderr가 이상하거나 문구가 201자면 error', async () => {
    for (const stderr of ['엉뚱한 출력\n', `${JSON.stringify({ type: 'INVALID_PROBE', message: '가'.repeat(201) })}\n`, `${JSON.stringify({ type: 'PROCESSING_FAILED', message: '내부' })}\n`, `${JSON.stringify({ type: 'INVALID_PROBE', message: '' })}\n`]) {
      const { result } = await runFake({ mode: 'exit', code: 2, stderr });
      assert.equal(result.kind, 'error', stderr);
    }
  });

  test('종료 1 -> error, cause는 4KB로 자른다', async () => {
    const { result } = await runFake({ mode: 'exit', code: 1, stderr: `{"type":"PROCESSING_FAILED"}\ncause: ${'x'.repeat(10_000)}` });
    assert.equal(result.kind, 'error');
    assert.equal(result.exitCode, 1);
    assert.equal(Buffer.byteLength(result.cause), 4096);
    assert.match(result.cause, /^\{"type":"PROCESSING_FAILED"\}\ncause: /);
  });

  test('자식이 스스로 SIGKILL -> crash', async () => {
    const { result } = await runFake({ mode: 'selfkill' });
    assert.equal(result.kind, 'crash');
    assert.equal(result.exitCode, null);
    assert.equal(result.signal, 'SIGKILL');
  });

  test('잠드는 자식 + 짧은 제한 시간 -> timeout, 자식이 사라진다', async () => {
    const { result, info } = await runFake({ mode: 'sleep' }, { timeoutMs: 1500 });
    assert.equal(result.kind, 'timeout');
    assert.ok(result.durationMs >= 1400);
    assert.ok(info?.pid, '자식이 시작 정보를 남겨야 한다(제한 시간이 너무 짧음)');
    assert.equal(alive(info.pid), false);
  });

  test('신호 abort() -> aborted, 자식이 사라진다', async () => {
    const controller = new AbortController();
    setTimeout(() => controller.abort(), 1500);
    const { result, info } = await runFake({ mode: 'sleep' }, { signal: controller.signal });
    assert.equal(result.kind, 'aborted');
    assert.ok(info?.pid);
    assert.equal(alive(info.pid), false);
  });

  test('이미 abort된 신호면 자식을 띄우지 않고 aborted', async () => {
    const { result, info } = await runFake({ mode: 'ok' }, { signal: AbortSignal.abort() });
    assert.equal(result.kind, 'aborted');
    assert.equal(info, null);
  });

  test('자식이 받은 환경 변수에 시크릿이 없다(PATH만)', async () => {
    process.env.IMAGE_PROCESSING_API_SECRET = SECRET;
    try {
      const { info } = await runFake({ mode: 'ok' });
      // macOS는 __CF_USER_TEXT_ENCODING을 프로세스가 스스로 더한다
      assert.ok(info.envKeys.includes('PATH'));
      assert.deepEqual(info.envKeys.filter((key) => key !== 'PATH' && key !== '__CF_USER_TEXT_ENCODING'), []);
    } finally {
      delete process.env.IMAGE_PROCESSING_API_SECRET;
    }
  });

  test('실행 파일을 찾을 수 없는 childPath -> error', async () => {
    const { result } = await runFake({ mode: 'ok' }, { childPath: join(root, 'nope.js') });
    assert.equal(result.kind, 'error');
  });
});

describe('실제 extract-child.js', () => {
  let dir;
  before(async () => {
    dir = await mkdtemp(join(tmpdir(), 'extract-child-'));
  });
  after(() => rm(dir, { recursive: true, force: true }));

  test('JPG가 아닌 파일 -> invalid (종료 2)', async () => {
    const probePath = join(dir, 'not-a-jpeg.txt');
    await writeFile(probePath, 'hello');
    const result = await runExtraction({ probePath, outDir: dir });
    assert.equal(result.kind, 'invalid');
    assert.equal(result.message, 'JPG 파일이 아니에요. Lightroom에서 JPG로 다시 내보내 주세요.');
  });

  test('없는 파일 -> error (CLI와 달리 작가 탓이 아니다)', async () => {
    const result = await runExtraction({ probePath: join(dir, 'missing.jpg'), outDir: dir });
    assert.equal(result.kind, 'error');
    assert.equal(result.exitCode, 1);
    const [first, second] = result.cause.split('\n');
    assert.deepEqual(JSON.parse(first), { type: 'PROCESSING_FAILED', message: '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.' });
    assert.match(second, /^cause: .*ENOENT/);
  });

  test('합성 견본 + XMP -> ok (recipe.schemaVersion 1, meta.branch)', { timeout: 120_000 }, async () => {
    const xmp = '<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"><rdf:Description xmlns:crs="http://ns.adobe.com/camera-raw-settings/1.0/" crs:Exposure2012="0"/></rdf:RDF></x:xmpmeta>';
    const probePath = join(dir, 'probe.jpg');
    await writeFile(probePath, await sharp(Buffer.from(inputProbe().buffer), { raw: { width: W, height: H, channels: 4 } }).removeAlpha().withXmp(xmp).jpeg({ quality: 100 }).toBuffer());
    const result = await runExtraction({ probePath, outDir: dir });
    assert.equal(result.kind, 'ok', result.cause);
    assert.equal(JSON.parse(result.recipeBytes.toString('utf8')).schemaVersion, 1);
    assert.ok(['M1', 'A2', 'G1'].includes(result.meta.branch));
    assert.equal(result.meta.probeVersion, 2);
  });
});
