// 지역 톤 C 읽기 합성 왕복 시험: 알려진 지역 대비 연산 → 견본에 적용 → 읽기 → 합성 사진에 다시 적용했을 때 원래 연산이 복원되는지.
// 문턱 근거: 두 결과 모두 8비트로 반올림되므로 연산이 같으면 ΔE는 반올림 몫(≈0.1)뿐이고, 눈에 보이기 시작하는 ΔE2000 ≈ 1의 3분의 1(0.3)을 넘으면 어긋난 것이다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyLut33, lutDeltaE } from '../src/m1/color.js';
import { extractM1 } from '../src/m1/extract.js';
import { H, W, clearProbeCaches, inputProbe } from '../src/m1/layout.js';
import { C_GRID, C_SIGMAS, fitToneLS, toneDeltaAt } from '../src/m1/local-tone.js';
import { applyTone } from './support/apply-stack.js';
import { identityLut, planOf, renderOutsideCore } from './support/probe-edit.js';
import { makeScene, meanDeltaE } from './support/scenes.js';

const TOL = 0.3;
const SW = 384, SH = 512;
const LofB = (B) => { const y = Math.max(0, Math.exp(B) - 0.001); return y > 216 / 24389 ? 116 * Math.cbrt(y) - 16 : (24389 / 27) * y; };

// 알려진 연산: 주변 밝기 대비 지역 대비 α·(Y − L*(B)) (±15로 자름)
function knownC(alpha, pct) {
  const ny = 17, nb = 9, bLo = -6, bHi = 0, T = new Float64Array(ny * nb);
  for (let k = 0; k < nb; k++) for (let j = 0; j < ny; j++) T[k * ny + j] = Math.max(-15, Math.min(15, alpha * ((100 * j) / (ny - 1) - LofB(bLo + ((bHi - bLo) * k) / (nb - 1)))));
  return { ny, yLo: 0, yHi: 100, nb, bLo, bHi, sigmaPct: pct, T };
}

describe('지역 톤 C 읽기', () => {
  let probe;
  const scenes = [0, 1, 2].map((k) => makeScene(k, SW, SH));
  before(() => { probe = inputProbe(); });
  after(() => clearProbeCaches());

  test('연산자: 0 표는 항등, 이중선형 표 배치, 매듭 밖은 끝 값', () => {
    const zero = { ...knownC(0.3, 0.02), T: new Float64Array(17 * 9) };
    assert.deepEqual(Array.from(applyTone(scenes[0], SW, SH, zero, new Uint8ClampedArray(scenes[0].length))), Array.from(scenes[0]));
    const t = { ny: 3, yLo: 0, yHi: 100, nb: 2, bLo: -4, bHi: 0, sigmaPct: 0.02, T: Float64Array.of(0, 1, 2, 10, 11, 12) };
    assert.equal(toneDeltaAt(t, 50, -4), 1);
    assert.equal(toneDeltaAt(t, 100, 0), 12);
    assert.equal(toneDeltaAt(t, 25, -2), 5.5);
    assert.equal(toneDeltaAt(t, -10, -9), 0);
  });

  test('최소제곱 맞춤: 표본이 표 자체에서 나오면 표를 되찾는다(행·열 배치)', () => {
    const truth = { ...C_GRID, sigmaPct: 0.02, T: Float64Array.from({ length: C_GRID.ny * C_GRID.nb }, (_, c) => Math.sin(c * 0.7) * 5) };
    const N = 200000, Y = new Float32Array(N), B = new Float32Array(N), R = new Float32Array(N);
    for (let s = 0; s < N; s++) { Y[s] = (s * 37.31) % 100; B[s] = -7 + ((s * 0.6180339) % 7); R[s] = toneDeltaAt(truth, Y[s], B[s]); }
    const fit = fitToneLS(Y, B, R, C_GRID, 0.02, 0); // 표본이 모든 칸을 촘촘히 덮으므로 이웃 벌점 없이 푼다
    const err = Math.max(...fit.T.map((v, c) => Math.abs(v - truth.T[c])));
    assert.ok(err < 0.05, `최대 차 ${err.toFixed(3)}`);
  });

  test('왕복: 코어 밖에만 걸린 지역 대비(σ 2%)는 σ를 맞게 고르고 복원된다', () => {
    const op = knownC(0.3, 0.02);
    const rendered = renderOutsideCore(probe, (p) => applyTone(p, W, H, op, new Uint8ClampedArray(p.length)));
    const m1 = extractM1({ rgba: rendered, width: W, height: H }, planOf({ C: true }));
    assert.equal(m1.V, null);
    assert.equal(m1.K, null);
    assert.equal(m1.grain, null);
    assert.equal(m1.C.sigmaPct, 0.02, `σ ${m1.C.sigmaPct}`);
    assert.ok(C_SIGMAS.includes(m1.C.sigmaPct));
    assert.ok(lutDeltaE(m1.lut, identityLut()).mean < 0.05, '코어가 입력 그대로이면 LUT는 항등이어야 해요');
    const lutOnly = Math.max(...scenes.map((sc) => meanDeltaE(applyLut33(sc, m1.lut, new Uint8ClampedArray(sc.length)), applyTone(sc, SW, SH, op, new Uint8ClampedArray(sc.length)), SW * SH)));
    const d = Math.max(...scenes.map((sc) => {
      const pre = applyLut33(sc, m1.lut, new Uint8ClampedArray(sc.length));
      return meanDeltaE(applyTone(pre, SW, SH, m1.C, new Uint8ClampedArray(sc.length)), applyTone(sc, SW, SH, op, new Uint8ClampedArray(sc.length)), SW * SH);
    }));
    assert.ok(lutOnly > TOL, `시험 연산이 너무 약해요 (LUT만 ${lutOnly.toFixed(3)})`);
    assert.ok(d < TOL, `왕복 ΔE ${d.toFixed(3)} ≥ ${TOL} (LUT만 ${lutOnly.toFixed(3)})`);
  });

  test('무편집 견본: C 표는 0에 가깝다', () => {
    const m1 = extractM1({ rgba: probe, width: W, height: H }, planOf({ C: true }));
    const c = Math.max(...m1.C.T.map(Math.abs));
    assert.ok(c < 1, `최대 |C| ${c.toFixed(2)}`);
  });
});
