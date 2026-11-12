// 그레인 읽기 합성 왕복 시험: 알려진 그레인 → 견본 평탄 패치 → 읽기 → 복원한 그레인의 통계(구간별 σ, 스펙트럼)가 원래와 같은지,
// 진폭 0이 정확한 항등인지, 같은 시드가 결정적인지 본다.
// 문턱은 원리에서 정했다:
//  - 해시 잡음 모멘트: 표본 N = 2^20이면 평균의 표준오차 1/√N ≈ 1e-3, 분산의 표준오차 √(2/N) ≈ 1.4e-3. 그 5배인 평균 5e-3, 분산 7e-3.
//  - σ 복원 5%, 스펙트럼 RMS 0.1(ln 파워의 구간 RMS 차). 같은 연산자의 다른 시드 사이 바닥이 문턱의 절반 미만인지 먼저 확인한다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { lstarPlane } from '../src/m1/color.js';
import { extractM1 } from '../src/m1/extract.js';
import {
  GRAIN_L_KNOTS, applyGrain, fitShape, grainField, kernel1D, measureFlats, modelShape, patchStats, specRms, whiteAt,
} from '../src/m1/grain.js';
import { FLATS, H, W, clearProbeCaches, grayOfL, inputProbe } from '../src/m1/layout.js';
import { planOf } from './support/probe-edit.js';

const SIGMA_TOL = 0.05, SPEC_TOL = 0.1;
const mean = (a) => a.reduce((s, v) => s + v, 0) / a.length;

// 알려진 그레인을 견본의 평탄 패치에만 건다
function grainedProbe(probe, known) {
  const data = Uint8ClampedArray.from(probe);
  for (const r of FLATS) {
    const patch = new Uint8ClampedArray(r.w * r.h * 4);
    for (let y = 0; y < r.h; y++) patch.set(probe.subarray(((r.y + y) * W + r.x) * 4, ((r.y + y) * W + r.x + r.w) * 4), y * r.w * 4);
    const out = applyGrain(patch, r.w, r.h, known, new Uint8ClampedArray(patch.length), { x0: r.x, y0: r.y, refW: W, refH: H });
    for (let y = 0; y < r.h; y++) data.set(out.subarray(y * r.w * 4, (y + 1) * r.w * 4), ((r.y + y) * W + r.x) * 4);
  }
  return data;
}

// L*가 L인 회색 패치에 그레인을 걸어 통계를 낸다. 패치 하나는 독립 표본이 적어 시드 4개의 평균을 쓴다
function statsAt(L, grain, seeds) {
  const v = grayOfL(L), rgba = new Uint8ClampedArray(256 * 256 * 4);
  for (let i = 0; i < 256 * 256; i++) { rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = v; rgba[i * 4 + 3] = 255; }
  const stats = seeds.map((seed) => patchStats(lstarPlane(applyGrain(rgba, 256, 256, { ...grain, seed }, new Uint8ClampedArray(rgba.length), { refW: W, refH: H }), 256 * 256)));
  return { hpStd: mean(stats.map((q) => q.hpStd)), spec: stats[0].spec.map((_, i) => mean(stats.map((q) => q.spec[i]))) };
}
const SEEDS_A = [11, 12, 13, 14], SEEDS_B = [21, 22, 23, 24];

describe('그레인 읽기', () => {
  let probe;
  before(() => { probe = inputProbe(); });
  after(() => clearProbeCaches());

  test('연산자: 해시 모멘트, 커널 에너지, 단위 분산, 자르기 일관성', () => {
    const N = 1 << 20;
    let s = 0, s2 = 0;
    for (let i = 0; i < N; i++) { const v = whiteAt(i % 1024, Math.floor(i / 1024), 5); s += v; s2 += v * v; }
    assert.ok(Math.abs(s / N) < 5e-3, `평균 ${s / N}`);
    assert.ok(Math.abs(s2 / N - 1) < 7e-3, `분산 ${s2 / N}`);
    assert.equal(whiteAt(3, 4, 9), whiteAt(3, 4, 9));
    assert.notEqual(whiteAt(3, 4, 9), whiteAt(3, 4, 10));
    assert.notEqual(whiteAt(3, 4, 9), whiteAt(4, 3, 9));
    for (const [sg, p, t] of [[0.46, 4.4, 0], [0.8, 2.2, 0], [0.8, 1.8, 1.8], [1.4, 2.3, 0]]) {
      const k = kernel1D(sg, p, t);
      assert.ok(Math.abs(Array.from(k).reduce((a, v) => a + v * v, 0) - 1) < 1e-9, `커널 에너지 ${sg},${p},${t}`);
    }
    for (const g of [{ size: 0.46 / 3072, edge: 4.4, tilt: 0 }, { size: 0.8 / 3072, edge: 1.8, tilt: 1.8 }]) {
      const f = grainField(256, 256, { ...g, seed: 3 }, { refW: 3072, refH: 4096 }), m = mean(Array.from(f)), v = mean(Array.from(f, (x) => (x - m) ** 2));
      assert.ok(Math.abs(v - 1) < 0.03, `잡음장 분산 ${v}`);
    }
    // 잘라낸 영역 = 전체의 같은 부분 (전역 좌표 해시)
    const gg = { size: 0.001, edge: 3, tilt: 0.5, seed: 2 };
    const full = grainField(96, 80, gg, { refW: 96, refH: 80 }), crop = grainField(40, 30, gg, { x0: 20, y0: 15, refW: 96, refH: 80 });
    for (let y = 0; y < 30; y++) for (let x = 0; x < 40; x++) assert.equal(crop[y * 40 + x], full[(y + 15) * 96 + x + 20]);
  });

  test('진폭 0은 정확한 항등이고 같은 시드는 결정적이다', () => {
    const zero = { amp: new Array(8).fill(0), size: 0.0003, edge: 4, tilt: 0, seed: 1 };
    const colorful = new Uint8ClampedArray(64 * 64 * 4).map((_, i) => (i % 4 === 3 ? 255 : (i * 37) % 256));
    assert.deepEqual(Array.from(applyGrain(colorful, 64, 64, zero, new Uint8ClampedArray(colorful.length))), Array.from(colorful));
    const g = { amp: [4, 5, 5, 4.5, 4, 3.5, 3, 2], size: 0.0003, edge: 4, tilt: 0, seed: 1 };
    const a = applyGrain(colorful, 64, 64, g, new Uint8ClampedArray(colorful.length)), b = applyGrain(colorful, 64, 64, g, new Uint8ClampedArray(colorful.length));
    const c = applyGrain(colorful, 64, 64, { ...g, seed: 2 }, new Uint8ClampedArray(colorful.length));
    assert.deepEqual(Array.from(a), Array.from(b));
    assert.notDeepEqual(Array.from(a), Array.from(c));
  });

  test('합성 무편집 견본의 평탄 패치는 고주파가 0이라 잡음 바닥을 뺄 필요가 없다', () => {
    assert.ok(measureFlats(probe).every((f) => f.hpStd === 0 && f.rawStd === 0));
  });

  test('무잡음 견본은 진폭 0으로 읽힌다', () => {
    const m1 = extractM1({ rgba: probe, width: W, height: H }, planOf({ grain: true }));
    assert.ok(m1.grain.amp.every((v) => v === 0));
    assert.ok(Number.isFinite(m1.grain.size) && Number.isFinite(m1.grain.edge), '진폭이 모두 0이어도 값이 NaN이면 안 돼요');
    assert.equal(m1.V, null);
  });

  test('스펙트럼 모델 재맞춤: 모델이 낸 모양을 다시 맞추면 RMS가 문턱의 절반 미만', () => {
    const f = fitShape(modelShape(0.8, 2.2, 1));
    assert.ok(f.rms < SPEC_TOL / 2, `RMS ${f.rms}`);
  });

  const CASES = [
    ['고운 백색형', { amp: [4, 5, 5, 4.5, 4, 3.5, 3, 2], size: 0.46 / 3072, edge: 4.4, tilt: 0 }],
    ['굵은 저역형', { amp: [2, 2.5, 2.5, 2.2, 2, 1.8, 1.5, 1], size: 0.8 / 3072, edge: 2.2, tilt: 0 }],
    ['푸른 잡음형', { amp: [6, 7, 7, 6.5, 6, 5.5, 5, 3.5], size: 0.8 / 3072, edge: 1.8, tilt: 1.8 }],
  ];
  for (const [name, k] of CASES) {
    test(`왕복: ${name} 그레인을 읽어 다른 시드로 걸면 구간 σ와 스펙트럼이 원래와 같다`, () => {
      const known = { ...k, seed: 11 };
      const m1 = extractM1({ rgba: grainedProbe(probe, known), width: W, height: H }, planOf({ grain: true }));
      assert.equal(m1.grain.amp.length, GRAIN_L_KNOTS.length);
      const fitted = { amp: m1.grain.amp, size: m1.grain.size, edge: m1.grain.edge, tilt: m1.grain.tilt };
      // 같은 연산자의 다른 시드 사이의 차이(측정 바닥)가 문턱의 절반 미만이어야 시험이 의미 있다
      const floor = specRms(statsAt(50, known, SEEDS_A).spec, statsAt(50, known, SEEDS_B).spec);
      assert.ok(floor < SPEC_TOL / 2, `바닥 ${floor.toFixed(3)}`);
      for (const L of [35, 50, 65, 80]) {
        const a = statsAt(L, known, SEEDS_A), b = statsAt(L, fitted, SEEDS_B);
        assert.ok(Math.abs(b.hpStd / a.hpStd - 1) <= SIGMA_TOL, `L* ${L}: σ ${b.hpStd.toFixed(3)} / ${a.hpStd.toFixed(3)}`);
        assert.ok(specRms(b.spec, a.spec) <= SPEC_TOL, `L* ${L}: 스펙트럼 RMS ${specRms(b.spec, a.spec).toFixed(3)}`);
      }
    });
  }
});
