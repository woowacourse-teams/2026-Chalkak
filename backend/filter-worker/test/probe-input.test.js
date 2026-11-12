import { before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import { INVALID_PROBE } from '../src/failure.js';
import { PROBE_HEIGHT, PROBE_WIDTH, checkProbeBytes, decodeProbe, readProbe } from '../src/probe-input.js';

const COLOR = { r: 200, g: 100, b: 50 };

function solidJpeg(width, height, pipelineOptions = (s) => s) {
  const image = sharp({ create: { width, height, channels: 3, background: COLOR } });
  return pipelineOptions(image).jpeg({ quality: 95 }).toBuffer();
}

function assertInvalidProbe(message) {
  return (error) => {
    assert.equal(error.type, INVALID_PROBE);
    if (message) assert.match(error.message, message);
    return true;
  };
}

describe('probe-input', () => {
  let validJpeg, validSmall;

  before(async () => {
    validJpeg = await solidJpeg(PROBE_WIDTH, PROBE_HEIGHT);
    validSmall = await solidJpeg(16, 16);
  });

  test('JPG가 아닌 바이트는 INVALID_PROBE', async () => {
    const png = await sharp({ create: { width: 8, height: 8, channels: 3, background: COLOR } }).png().toBuffer();
    assert.throws(() => checkProbeBytes(png), assertInvalidProbe(/JPG 파일이 아니에요/));
  });

  test('빈 버퍼는 INVALID_PROBE', () => {
    assert.throws(() => checkProbeBytes(Buffer.alloc(0)), assertInvalidProbe());
  });

  test('maxBytes를 넘으면 INVALID_PROBE', () => {
    assert.throws(() => checkProbeBytes(validJpeg, { maxBytes: 10 }), assertInvalidProbe(/너무 커요/));
  });

  test('3072x4096 JPG는 그대로 디코딩된다', async () => {
    const { rgba, width, height } = await readProbe(validJpeg);
    assert.equal(width, PROBE_WIDTH);
    assert.equal(height, PROBE_HEIGHT);
    assert.equal(rgba.length, width * height * 4);
    for (const pixel of [0, Math.floor((width * height) / 2), width * height - 1]) {
      const i = pixel * 4;
      assert.ok(Math.abs(rgba[i] - COLOR.r) <= 3);
      assert.ok(Math.abs(rgba[i + 1] - COLOR.g) <= 3);
      assert.ok(Math.abs(rgba[i + 2] - COLOR.b) <= 3);
      assert.equal(rgba[i + 3], 255);
    }
  });

  // 4096x3072 원본: 왼쪽 절반 빨강, 오른쪽 절반 파랑
  async function twoHalvesJpeg(orientation) {
    const w = PROBE_HEIGHT;
    const h = PROBE_WIDTH;
    const raw = Buffer.alloc(w * h * 3);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 3;
        if (x < w / 2) raw[i] = 255;
        else raw[i + 2] = 255;
      }
    }
    return sharp(raw, { raw: { width: w, height: h, channels: 3 } })
      .withMetadata({ orientation })
      .jpeg({ quality: 95 })
      .toBuffer();
  }

  function pixelAt({ rgba, width }, x, y) {
    const i = (y * width + x) * 4;
    return [rgba[i], rgba[i + 1], rgba[i + 2]];
  }

  for (const orientation of [6, 5]) {
    test(`EXIF 방향 ${orientation}로 저장된 4096x3072 JPG는 3072x4096으로 세워지고 위가 빨강, 아래가 파랑이다`, async () => {
      const decoded = await decodeProbe(await twoHalvesJpeg(orientation));
      assert.equal(decoded.width, PROBE_WIDTH);
      assert.equal(decoded.height, PROBE_HEIGHT);
      const [tr, tg, tb] = pixelAt(decoded, 1536, 500);
      assert.ok(tr > 240 && tg < 15 && tb < 15, `위쪽은 빨강이어야 한다: ${[tr, tg, tb]}`);
      const [br, bg, bb] = pixelAt(decoded, 1536, 3500);
      assert.ok(br < 15 && bg < 15 && bb > 240, `아래쪽은 파랑이어야 한다: ${[br, bg, bb]}`);
    });
  }

  test('흑백 JPG도 알파 255의 RGBA 4채널로 디코딩된다', async () => {
    const gray = await sharp({ create: { width: PROBE_WIDTH, height: PROBE_HEIGHT, channels: 3, background: COLOR } })
      .toColourspace('b-w')
      .jpeg({ quality: 95 })
      .toBuffer();
    const { rgba, width, height } = await decodeProbe(gray);
    assert.equal(rgba.length, width * height * 4);
    assert.equal(rgba[3], 255);
    assert.equal(rgba[rgba.length - 1], 255);
  });

  test('크기가 다르면 INVALID_PROBE', async () => {
    const wrongSize = await solidJpeg(3000, 4000);
    await assert.rejects(decodeProbe(wrongSize), assertInvalidProbe(/자르기·크기 조정/));
  });

  // 작은 JPG의 SOF 헤더에 큰 크기를 적어, 실제로 큰 이미지를 만들지 않고 "큰 크기로 보이는" 파일을 얻는다
  function jpegClaimingSize(width, height) {
    const bytes = Buffer.from(validSmall);
    const sof = bytes.findIndex((b, i) => b === 0xff && b !== bytes[i + 1] && (bytes[i + 1] === 0xc0 || bytes[i + 1] === 0xc2));
    bytes.writeUInt16BE(height, sof + 5);
    bytes.writeUInt16BE(width, sof + 7);
    return bytes;
  }

  test('한도를 넘는 큰 크기는 픽셀을 디코딩하기 전에 크기 안내로 빠르게 거절한다', async () => {
    const huge = jpegClaimingSize(16000, 16000);
    const rssBefore = process.memoryUsage().rss;
    const started = performance.now();
    await assert.rejects(decodeProbe(huge), assertInvalidProbe(/자르기·크기 조정/));
    assert.ok(performance.now() - started < 1000, '1초 안에 거절해야 한다');
    assert.ok(process.memoryUsage().rss - rssBefore < 100 * 1024 * 1024, 'RSS가 크게 늘면 안 된다');
  });

  test('실제로 큰 JPG(4096x4096)도 크기 안내로 거절한다', async () => {
    await assert.rejects(decodeProbe(await solidJpeg(4096, 4096)), assertInvalidProbe(/자르기·크기 조정/));
  });

  test('가로·세로가 맞아도 방향이 없어 뒤집히면(3072x4096 아닌 4096x3072) 거절한다', async () => {
    await assert.rejects(decodeProbe(await solidJpeg(PROBE_HEIGHT, PROBE_WIDTH)), assertInvalidProbe(/자르기·크기 조정/));
  });

  test('FF D8 FF로 시작하지만 깨진 JPG는 INVALID_PROBE', async () => {
    const truncated = validJpeg.subarray(0, 2000);
    await assert.rejects(readProbe(truncated), assertInvalidProbe(/사진을 읽을 수 없어요/));
  });

  test('sRGB가 아닌 ICC 프로필은 sRGB로 변환된다', async () => {
    // withIccProfile은 sRGB 픽셀을 P3 값으로 바꿔 저장한다. 프로필을 유지한 채 읽으면 원본과 다른 값이 보여야 한다
    const p3 = await solidJpeg(PROBE_WIDTH, PROBE_HEIGHT, (s) => s.withIccProfile('p3'));
    const stored = await sharp(p3).keepIccProfile().raw().toBuffer();
    assert.ok(Math.abs(stored[0] - COLOR.r) > 3 || Math.abs(stored[1] - COLOR.g) > 3, '저장된 값은 P3 기준이어야 한다');

    const { rgba } = await decodeProbe(p3);
    assert.ok(Math.abs(rgba[0] - COLOR.r) <= 3);
    assert.ok(Math.abs(rgba[1] - COLOR.g) <= 3);
    assert.ok(Math.abs(rgba[2] - COLOR.b) <= 3);
  });
});
