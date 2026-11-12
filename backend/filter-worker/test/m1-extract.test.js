// M1 통합 읽기 합성 왕복 시험: 알려진 스택(점 연산 LUT + C(Y, B) + k_b(0-1 px 포함) + 비네팅 + 그레인)을 순서대로 견본에 걸고,
// 견본 한 장 읽기(extractM1)가 스택을 복원하는지 본다. 단일 연산자 시험과 다른 점은 LUT가 항등이 아니라는 것이다(기준이 무편집이면 깨지는 경우).
// 판정은 합성 장면에서: 알려진 스택(그레인 뺌)과 읽은 스택(그레인 뺌)의 ΔE가 TOL 미만이고 LUT만보다 작아야 한다. 연산자마다 값도 몇 가지 본다.
// 문턱 근거: 견본 LUT는 33³ 칸 4×4 평균이라 그레인·대역 연산 잡음을 먹고, C·k_b는 견본 패널에서 완전히 갈리지 않는다.
//   그래서 단일 연산자 시험의 0.3보다 넓게 1.0(ΔE2000 ≈ 1, 눈에 보이기 시작하는 차이)으로 둔다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { lutDeltaE } from '../src/m1/color.js';
import { KIND_AMP0, makeBandGain } from '../src/m1/detail.js';
import { extractM1 } from '../src/m1/extract.js';
import { H, N, W, clearProbeCaches, gridValue, inputProbe } from '../src/m1/layout.js';
import { C_GRID } from '../src/m1/local-tone.js';
import { NRS, vignetteGainAt } from '../src/m1/vignette.js';
import { applyStack, vignetteFromSeparable } from './support/apply-stack.js';
import { identityLut, planOf } from './support/probe-edit.js';
import { makeScene, meanDeltaE } from './support/scenes.js';

const TOL = 1.0;
const SW = 384, SH = 512;

// ① LUT: 노출을 조금 내리고(−0.3 EV 상당) S자 대비 + 따뜻하게. 33³ 격자 값으로 직접 정의한다(그러면 LUT 자체는 정확히 표현됨)
const lutTrue = new Uint8Array(N ** 3 * 3);
for (let c = 0; c < N ** 3; c++) {
  const rgb = [c % N, Math.floor(c / N) % N, Math.floor(c / (N * N))].map((q) => gridValue(q) / 255);
  const s = (x) => { const y = x * 0.88; return y + 0.12 * Math.sin(2 * Math.PI * y) / (2 * Math.PI); };
  const out = [s(rgb[0]) * 1.04, s(rgb[1]), s(rgb[2]) * 0.93];
  for (let ch = 0; ch < 3; ch++) lutTrue[c * 3 + ch] = Math.min(255, Math.max(0, Math.round(out[ch] * 255)));
}
// ③ C(Y, B): 밝은 쪽을 누르되 주변이 밝을수록 더(밝은 영역 −에 가까운 모양), σ 짧은 변 2%
const Ctrue = { ...C_GRID, sigmaPct: 0.02, T: new Float64Array(C_GRID.ny * C_GRID.nb) };
for (let k = 0; k < C_GRID.nb; k++) for (let j = 0; j < C_GRID.ny; j++) {
  const Y = (100 * j) / (C_GRID.ny - 1), B = C_GRID.bLo + ((C_GRID.bHi - C_GRID.bLo) * k) / (C_GRID.nb - 1);
  Ctrue.T[k * C_GRID.ny + j] = -4 * Math.max(0, (Y - 55) / 45) ** 1.5 * (0.5 + 0.5 * Math.min(1, Math.max(0, (B + 4) / 4)));
}
// ④ k_b: 0-1·1-2 px 대역을 키우는 샤프닝 꼴(진폭·밝기와 무관한 상수)
const Ktrue = makeBandGain(KIND_AMP0), per = Ktrue.eKnots.length * Ktrue.mKnots.length;
[0.5, 0.35, 0.1, 0, 0, 0].forEach((g, b) => { for (let q = 0; q < per; q++) Ktrue.K[b * per + q] = g; });
// ⑤ 비네팅: 타원, 중간점 0.7 근처 로지스틱, 하이라이트가 덜 걸림
const SV = Float64Array.of(0.85, 0.95, 1, 1, 1, 0.95, 0.8, 0.6);
const uTrue = Float64Array.from({ length: NRS }, (_, i) => { const d = i / (NRS - 1), z = (q) => 1 / (1 + Math.exp(-(q - 0.7) / 0.08)); return -0.7 * (z(d) - z(0)) / (z(1) - z(0)); });
const Vtrue = { ...vignetteFromSeparable(uTrue, SV, 'dark'), shape: { p: 2, c: 1 } };
// ⑥ 그레인: 그레인 50 근처 모양, 진폭 L* 1.0
const Gtrue = { amp: new Array(8).fill(1.0), size: 0.46 / W, edge: 4.4, tilt: 0, seed: 3 };
const P0 = { lut: lutTrue, C: Ctrue, K: Ktrue, V: Vtrue, grain: Gtrue };
const ALL = { V: true, C: true, K: true, grain: false };

describe('M1 통합 읽기', () => {
  let R;
  const scenes = [0, 1, 2].map((k) => makeScene(k, SW, SH));
  const dE = (a, b) => meanDeltaE(a, b, SW * SH);

  before(() => {
    const synth = applyStack(inputProbe(), W, H, P0, { ...ALL, grain: true });
    R = extractM1({ rgba: synth, width: W, height: H }, planOf({ V: true, C: true, K: true, grain: true }));
  });
  after(() => clearProbeCaches());

  test('비네팅: dark로 읽고, 가운데 이득은 0이며 모서리 이득은 참과 20% 안쪽이다(점 연산 LUT가 있어도)', () => {
    assert.equal(R.V.mode, 'dark');
    const center = vignetteGainAt(R.V, 0.1, 58), corner = vignetteGainAt(R.V, 1, 58), truth = vignetteGainAt(Vtrue, 1, 58);
    assert.ok(Math.abs(center) < 0.02, `가운데 이득 ${center}`);
    assert.ok(Math.abs(corner / truth - 1) < 0.2, `모서리 이득 ${corner.toFixed(3)} / 참 ${truth.toFixed(3)}`);
  });

  test('LUT 노드 오차와 그레인 진폭·크기', () => {
    const lutErr = lutDeltaE(R.lut, lutTrue);
    assert.ok(lutErr.mean < 1.0, `LUT 노드 ΔE ${lutErr.mean.toFixed(3)}`);
    const ampErr = [2, 3, 4, 5].map((k) => R.grain.amp[k] / Gtrue.amp[k] - 1);
    assert.ok(ampErr.every((e) => Math.abs(e) < 0.2), `그레인 진폭 오차 ${ampErr.map((e) => e.toFixed(2))}`);
    assert.ok(Math.abs(R.grain.size * W / 0.46 - 1) < 0.2, `크기 ${(R.grain.size * W).toFixed(2)} px`);
  });

  test('합성 장면 왕복(그레인 뺌): 복원한 스택이 알려진 스택에 가깝고 LUT만보다 낫다', () => {
    for (const sc of scenes) {
      const truth = applyStack(sc, SW, SH, P0, ALL), full = applyStack(sc, SW, SH, R, ALL);
      const lutOnly = applyStack(sc, SW, SH, R, { V: false, C: false, K: false, grain: false });
      assert.ok(dE(full, truth) < TOL, `복원 스택 ΔE ${dE(full, truth).toFixed(3)} ≥ ${TOL}`);
      assert.ok(dE(full, truth) < dE(lutOnly, truth), '복원 스택이 LUT만보다 나빠요');
    }
  });

  test('무편집 견본: 모든 연산자를 켜도 값이 0에 가깝다', () => {
    const m1 = extractM1({ rgba: inputProbe(), width: W, height: H }, planOf({ V: true, C: true, K: true, grain: true }));
    assert.ok(Math.max(...m1.V.T.map(Math.abs)) < 0.02, `V ${Math.max(...m1.V.T.map(Math.abs))}`);
    assert.ok(Math.max(...m1.C.T.map(Math.abs)) < 1, `C ${Math.max(...m1.C.T.map(Math.abs))}`);
    assert.ok(Math.max(...Array.from(m1.K.K, Math.abs)) < 0.05, `K ${Math.max(...Array.from(m1.K.K, Math.abs))}`);
    assert.ok(m1.grain.amp.every((v) => v === 0));
    assert.ok(lutDeltaE(m1.lut, identityLut()).max < 0.01);
  });
});
