// 디테일 k_b 읽기 합성 왕복 시험: 알려진 대역 이득(0-1 px 언샤프, 코어링)을 견본에 걸고 읽은 뒤 합성 사진에 다시 적용했을 때 원래 연산이 복원되는지.
// 문턱 근거: 비네팅·지역 톤 시험과 같다(두 결과 모두 8비트 반올림이라 ΔE 0.3을 넘으면 어긋난 것).
// 알려진 연산은 연산자 꼴 안(같은 DoG 대역·같은 매듭)에 두어 복원이 원리상 정확해야 하는 경우만 단언한다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyLut33 } from '../src/m1/color.js';
import { BAND_E_KNOTS, BAND_M_KNOTS, KIND_AMP0, bandFeatures, bandGainDelta, bandKnotWeights, makeBandGain } from '../src/m1/detail.js';
import { extractM1 } from '../src/m1/extract.js';
import { H, W, clearProbeCaches, inputProbe } from '../src/m1/layout.js';
import { applyBandGain } from './support/apply-stack.js';
import { planOf, renderOutsideCore } from './support/probe-edit.js';
import { makeScene, meanDeltaE, mulberry } from './support/scenes.js';

const TOL = 0.3;
const SW = 384, SH = 512;

// 표 틀(0-1 px 대역 포함) 위에 f(대역, 포락 매듭, 평균 매듭)로 값을 채운다
function ampOp(f) {
  const kb = makeBandGain(KIND_AMP0), nb = kb.sigmas.length - 1, ne = kb.eKnots.length, nm = kb.mKnots.length;
  for (let b = 0; b < nb; b++) for (let j = 0; j < ne; j++) for (let l = 0; l < nm; l++) kb.K[(b * ne + j) * nm + l] = f(b, j, l);
  return kb;
}
const OPS = {
  // 고전 언샤프 L + 1.0 · (L − blur(L, 1)): 0-1 px 대역 상수 이득
  unsharp01: ampOp((b) => (b === 0 ? 1 : 0)),
  // 코어링(노이즈 감소 꼴): 작은 포락은 지우고 큰 포락은 남긴다 (0-1, 16-32 px 대역은 그대로)
  coring: ampOp((b, j) => (b === 0 || b === 5 ? 0 : [-1, -0.8, -0.2, 0][j])),
};

describe('디테일 k_b 읽기', () => {
  let probe;
  const scenes = [0, 1, 2].map((k) => makeScene(k, SW, SH));
  const band = (rgba, kb) => applyBandGain(rgba, SW, SH, kb, new Uint8ClampedArray(rgba.length));
  before(() => { probe = inputProbe(); });
  after(() => clearProbeCaches());

  test('연산자: 0 표는 항등, 표 배치, 사인 포락 = 대역 진폭', () => {
    const zero = makeBandGain(KIND_AMP0);
    assert.deepEqual(Array.from(band(scenes[0], zero)), Array.from(scenes[0]));
    // 표 배치: 매듭 위에서는 그 값, 매듭 밖은 끝 값
    const kb = ampOp((b, j, l) => 100 * b + 10 * j + l), idx = [0, 0, 0, 0], wt = [0, 0, 0, 0], per = BAND_E_KNOTS.length * BAND_M_KNOTS.length;
    const at = (b, E, M) => { const c = bandKnotWeights(kb, E, M, idx, wt); let g = 0; for (let q = 0; q < c; q++) g += wt[q] * kb.K[b * per + idx[q]]; return g; };
    assert.ok(Math.abs(at(2, 2.4, 55) - 221) < 1e-9 && Math.abs(at(1, 0.01, 10) - 100) < 1e-9 && Math.abs(at(4, 1e3, 99) - 432) < 1e-9);
    assert.ok(Math.abs(at(0, Math.sqrt(0.15 * 0.6), 42.5) - 5.5) < 1e-9, '매듭 사이는 log E·M 이중선형이어야 해요');
    // 사인파의 포락은 그 대역 진폭과 같다 (가운데 열에서). 0-1 px 대역은 주기 16 사인을 거의 싣지 않고 포락 흐림도 2 px뿐이라 보지 않는다
    const w = 256, h = 64, P = 16, A = 5, L = new Float32Array(w * h);
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) L[y * w + x] = 50 + A * Math.sin((2 * Math.PI * x) / P);
    const f = bandFeatures(L, w, h, kb), mid = (h / 2) * w;
    let worst = 0;
    f.bands.forEach((D, b) => {
      if (b === 0) return;
      let mx = 0;
      for (let x = 64; x < 192; x++) mx = Math.max(mx, Math.abs(D[mid + x]));
      if (mx > 0.05 * A) worst = Math.max(worst, Math.abs(f.env[b][mid + 128] - mx) / mx);
    });
    assert.ok(worst < 0.1, `상대 차 ${worst.toFixed(3)}`);
  });

  test('메모리를 아낀 계산(대역 하나씩, 표본 위치만)이 모든 평면을 만드는 계산과 같은 값을 준다', () => {
    const w = 200, h = 150, rand = mulberry(5), L = Float32Array.from({ length: w * h }, (_, i) => 50 + 30 * Math.sin(i / 37) + 8 * (rand() - 0.5));
    const kb = ampOp((b, j, l) => Math.sin(b + 2 * j + 3 * l)), f = bandFeatures(L, w, h, kb), per = kb.eKnots.length * kb.mKnots.length, idx = [0, 0, 0, 0], wt = [0, 0, 0, 0];
    const ref = new Float32Array(w * h);
    f.bands.forEach((D, b) => {
      for (let i = 0; i < ref.length; i++) {
        const c = bandKnotWeights(kb, f.env[b][i], f.mean[i], idx, wt);
        let g = 0;
        for (let q = 0; q < c; q++) g += wt[q] * kb.K[b * per + idx[q]];
        ref[i] += D[i] * g;
      }
    });
    const full = bandGainDelta(L, w, h, kb), only = Int32Array.from({ length: 500 }, (_, q) => (q * 59) % (w * h)), part = bandGainDelta(L, w, h, kb, 1, only);
    assert.deepEqual(Array.from(full), Array.from(ref), '전체 계산이 연구 방식과 달라요');
    assert.deepEqual(Array.from(part), Array.from(only, (i) => ref[i]), '표본 위치 계산이 달라요');
  });

  for (const [name, op] of Object.entries(OPS)) {
    test(`왕복: ${name}(코어 밖)은 LUT만보다 훨씬 가깝게 복원된다`, () => {
      const rendered = renderOutsideCore(probe, (p) => applyBandGain(p, W, H, op, new Uint8ClampedArray(p.length)));
      const m1 = extractM1({ rgba: rendered, width: W, height: H }, planOf({ K: true }));
      assert.equal(m1.C, null);
      assert.deepEqual(m1.K.sigmas, KIND_AMP0.sigmas);
      const target = scenes.map((sc) => band(sc, op));
      const lutOnly = Math.max(...scenes.map((sc, i) => meanDeltaE(applyLut33(sc, m1.lut, new Uint8ClampedArray(sc.length)), target[i], SW * SH)));
      const d = Math.max(...scenes.map((sc, i) => meanDeltaE(band(applyLut33(sc, m1.lut, new Uint8ClampedArray(sc.length)), m1.K), target[i], SW * SH)));
      assert.ok(lutOnly > TOL, `시험 연산이 너무 약해요 (LUT만 ${lutOnly.toFixed(3)})`);
      assert.ok(d < TOL, `왕복 ΔE ${d.toFixed(3)} ≥ ${TOL} (LUT만 ${lutOnly.toFixed(3)})`);
    });
  }

  test('무편집 견본: 표가 0이다', () => {
    const m1 = extractM1({ rgba: probe, width: W, height: H }, planOf({ K: true })), mx = Math.max(...Array.from(m1.K.K, Math.abs));
    assert.ok(mx < 1e-6, `최대 |k| ${mx}`);
  });
});
