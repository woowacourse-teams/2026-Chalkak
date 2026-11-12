// 견본 배치·마커 정합·LUT 읽기 시험. 합성 무편집 견본(입력 그대로)을 읽으면 항등 LUT가 나오고 아무것도 켜지지 않아야 한다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyLut33, lutDeltaE } from '../src/m1/color.js';
import { extractCoreLut } from '../src/m1/core-lut.js';
import { extractM1, toRecipeJson } from '../src/m1/extract.js';
import {
  CORE_COLS, CORE_ROWS, H, INVALID_PROBE_MESSAGE, LAYOUT, N, W, checkLayout, clearProbeCaches, inputProbe, registerMarkers,
} from '../src/m1/layout.js';
import { fitProbeVignette } from '../src/m1/probe-vignette.js';
import { INVALID_PROBE } from '../src/failure.js';
import { identityLut, planOf } from './support/probe-edit.js';
import { mulberry } from './support/scenes.js';

const assertInvalidProbe = (error) => {
  assert.equal(error.type, INVALID_PROBE);
  assert.equal(error.message, '받은 견본 이미지를 그대로 사용해 주세요.');
  return true;
};

describe('견본 배치·정합·LUT', () => {
  let probe;
  before(() => { probe = inputProbe(); });
  after(() => clearProbeCaches());

  test('배치: 영역이 캔버스 안에 있고 완충띠 간격을 지키며, 코어에 33³ 색이 한 번씩 있다', () => {
    assert.deepEqual(checkLayout(), []);
    assert.equal(probe.length, W * H * 4);
    const seen = new Uint8Array(N ** 3);
    let gray = 0;
    for (const c of LAYOUT.coreGrid) { if (c < 0) gray++; else seen[c]++; }
    assert.ok(seen.every((v) => v === 1));
    assert.equal(gray, CORE_COLS * CORE_ROWS - N ** 3);
  });

  test('무편집 견본: 항등 LUT를 읽고 비네팅 표는 "없음"이다', () => {
    assert.deepEqual(lutDeltaE(extractCoreLut(probe), identityLut()), { mean: 0, max: 0 });
    const vig = fitProbeVignette(probe);
    assert.ok(!vig.applied);
    assert.match(vig.reason, /비네팅 없음/);
  });

  test('33³ 조회: 항등 LUT는 색을 한 단계 이내로 그대로 둔다', () => {
    const rand = mulberry(3), px = Uint8ClampedArray.from({ length: 4000 }, (_, i) => (i % 4 === 3 ? 255 : rand() * 256));
    const out = applyLut33(px, identityLut(), new Uint8ClampedArray(px.length));
    assert.ok(px.every((v, i) => Math.abs(v - out[i]) <= 1));
  });

  test('마커 정합: 무편집은 어긋남·경고가 없고, 1 px 어긋나면 경고하며, 3 px 넘게 어긋나면 INVALID_PROBE', () => {
    const ok = registerMarkers(probe, W, H);
    assert.deepEqual(ok.warnings, []);
    assert.ok(ok.markers.every((m) => m.dx === 0 && m.dy === 0 && m.score > 0.99));
    const shifted = new Uint8ClampedArray(probe.length);
    shifted.set(probe.subarray(4)); // 한 픽셀 왼쪽으로
    const reg = registerMarkers(shifted, W, H);
    assert.ok(reg.markers.every((m) => m.dx === -1 && m.dy === 0) && reg.warnings.length === 4, JSON.stringify(reg.markers));
    const far = new Uint8ClampedArray(probe.length);
    far.set(probe.subarray(4 * 5)); // 다섯 픽셀
    assert.throws(() => registerMarkers(far, W, H), assertInvalidProbe);
  });

  test('마커가 1 px 어긋나면 작가용 경고 한 문장을 돌려주고 읽은 값은 그대로다', () => {
    const shifted = new Uint8ClampedArray(probe.length);
    shifted.set(probe.subarray(4));
    const m1 = extractM1({ rgba: shifted, width: W, height: H }, planOf({}));
    assert.deepEqual(m1.warnings, ['견본 위치가 조금 어긋나 있어요. 받은 견본을 자르거나 크기를 바꾸지 않고 내보냈는지 확인해 주세요.']);
    assert.ok(m1.warnings.every((w) => !/마커|px/.test(w)));
  });

  test('어긋남이 없으면 경고도 없다', () => {
    assert.deepEqual(extractM1({ rgba: probe, width: W, height: H }, planOf({})).warnings, []);
  });

  test('크기가 다르면 INVALID_PROBE(한국어 안내)', () => {
    assert.equal(INVALID_PROBE_MESSAGE, '받은 견본 이미지를 그대로 사용해 주세요.');
    assert.throws(() => registerMarkers(new Uint8ClampedArray(1536 * 2048 * 4), 1536, 2048), assertInvalidProbe);
    assert.throws(() => extractM1({ rgba: new Uint8ClampedArray(16), width: 2, height: 2 }, planOf({})), assertInvalidProbe);
  });

  test('마커 어긋남은 연산자를 켜도 INVALID_PROBE로 끝난다(읽기를 시작하지 않는다)', () => {
    const far = new Uint8ClampedArray(probe.length);
    far.set(probe.subarray(4 * 5));
    assert.throws(() => extractM1({ rgba: far, width: W, height: H }, planOf({ V: true, C: true, K: true, grain: true })), assertInvalidProbe);
  });

  test('모든 연산자를 끄면 LUT만 읽고 나머지는 null이다', () => {
    const m1 = extractM1({ rgba: probe, width: W, height: H }, planOf({}));
    assert.deepEqual(lutDeltaE(m1.lut, identityLut()), { mean: 0, max: 0 });
    assert.deepEqual([m1.V, m1.C, m1.K, m1.grain], [null, null, null, null]);
    assert.ok(m1.lut instanceof Uint8Array && m1.lut.length === N ** 3 * 3);
  });

  test('레시피 JSON: 형식화 배열이 배열이 되어 JSON으로 왕복되고 꺼진 연산자는 null이다', () => {
    const m1 = extractM1({ rgba: probe, width: W, height: H }, planOf({ grain: true })), json = toRecipeJson(m1);
    assert.deepEqual(JSON.parse(JSON.stringify(json)), json);
    assert.ok(Array.isArray(json.lut) && json.lut.length === N ** 3 * 3);
    assert.ok(Array.isArray(json.grain.amp) && json.grain.amp.length === 8);
    assert.deepEqual([json.V, json.C, json.K], [null, null, null]);
  });
});
