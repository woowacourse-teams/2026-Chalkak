// 비네팅 V 읽기 합성 왕복 시험: 알려진 형태(타원·원·둥근 사각형)·반경 곡선·밝기 응답을 견본에 걸고,
// 견본 한 장 읽기가 형태와 연산을 복원하는지 본다.
// 문턱 근거: 두 결과 모두 8비트로 반올림되므로 같은 연산이면 ΔE는 반올림 몫(≈0.1)뿐이고, 눈에 보이기 시작하는 ΔE2000 ≈ 1의 3분의 1(0.3)을 넘으면 어긋난 것이다.
import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { H, W, clearProbeCaches, inputProbe } from '../src/m1/layout.js';
import {
  ELLIPSE, NRS, VIG_V_KNOTS, clearVignetteCaches, extractSamples, gains, readShape, shapeDist, vignetteGainAt,
} from '../src/m1/vignette.js';
import { applyVignette, vignetteFromSeparable } from './support/apply-stack.js';
import { makeScene, meanDeltaE } from './support/scenes.js';

const TOL = 0.3;
const SW = 384, SH = 512;
const SV = Float64Array.of(0.85, 0.95, 1, 1, 1, 0.95, 0.8, 0.6); // 하이라이트 쪽이 덜 걸리는 알려진 밝기 응답 s(v)

// 알려진 반경 곡선: 중심 d0에서 시작해 폭 wd로 깊어지는 매끄러운 계단(로지스틱) × 세기. dark면 ln 이득(음수), light면 흰색 섞음
function knownOp(mode, amount, d0, wd, shape) {
  const u = Float64Array.from({ length: NRS }, (_, i) => {
    const d = i / (NRS - 1), z = (q) => 1 / (1 + Math.exp(-(q - d0) / wd));
    return (mode === 'light' ? 1 : -1) * amount * (z(d) - z(0)) / (z(1) - z(0));
  });
  return { ...vignetteFromSeparable(u, SV, mode), shape };
}

describe('비네팅 V 읽기', () => {
  let probe;
  const scenes = [0, 1, 2].map((k) => makeScene(k, SW, SH));
  const applyScene = (sc, vig) => applyVignette(sc, SW, SH, vig, new Uint8ClampedArray(sc.length));
  const render = (vig) => applyVignette(probe, W, H, vig, new Uint8ClampedArray(probe.length));

  before(() => { probe = inputProbe(); });
  after(() => { clearProbeCaches(); clearVignetteCaches(); });

  test('연산자: 표 0은 항등, 모서리 거리 1, 중심 거리 0, 타원은 shape 없는 표와 같다', () => {
    const zero = { nr: NRS, nv: VIG_V_KNOTS.length, vKnots: VIG_V_KNOTS, mode: 'dark', T: new Float64Array(NRS * VIG_V_KNOTS.length), shape: { p: 8, c: 1.2 } };
    assert.deepEqual(Array.from(applyScene(scenes[1], zero)), Array.from(scenes[1]));
    for (const shape of [{ p: 2, c: 1 }, { p: 2, c: 4 / 3 }, { p: 12, c: 1 }, { p: 1.5, c: 0.8 }]) {
      for (const [x, y] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) assert.ok(Math.abs(shapeDist(x, y, shape) - 1) < 1e-12, JSON.stringify(shape));
      assert.equal(shapeDist(0, 0, shape), 0);
    }
    const op = knownOp('dark', 2, 0.7, 0.07, ELLIPSE), noShape = { ...op, shape: undefined };
    assert.deepEqual(Array.from(applyScene(scenes[0], op)), Array.from(applyScene(scenes[0], noShape)));
    // 이중선형 조회: 매듭 위에서는 표 값
    const vig = vignetteFromSeparable(Float64Array.from({ length: 32 }, (_, i) => -i / 31), SV, 'dark');
    assert.ok(Math.abs(vignetteGainAt(vig, 1, 58) - -SV[4]) < 1e-12);
  });

  const roundTrip = (op, check) => {
    const S = extractSamples(probe, render(op)), R = readShape(S);
    assert.equal(R.mode, op.mode, '방향을 잘못 읽었어요');
    check(R);
    const d = Math.max(...scenes.map((sc) => meanDeltaE(applyScene(sc, op), applyScene(sc, R.vig), SW * SH)));
    assert.ok(d < TOL, `복원 ΔE ${d.toFixed(3)} ≥ ${TOL}`);
  };

  test('왕복: 타원 기본(중간점 0.7)은 타원으로 남는다', () => {
    roundTrip(knownOp('dark', 2, 0.7, 0.07, ELLIPSE), (R) => assert.deepEqual(R.shape, ELLIPSE));
  });

  test('왕복: 원(픽셀, c 4/3)을 읽는다', () => {
    roundTrip(knownOp('dark', 2, 0.7, 0.07, { p: 2, c: 4 / 3 }), (R) => assert.ok(Math.abs(R.shape.p - 2) < 0.2 && Math.abs(R.shape.c - 4 / 3) < 0.03, JSON.stringify(R.shape)));
  });

  test('왕복: 둥근 사각형(p 12, 가장자리 띠)을 읽는다', () => {
    roundTrip(knownOp('dark', 1.5, 0.95, 0.012, { p: 12, c: 1 }), (R) => assert.ok(R.shape.p > 7 && Math.abs(R.shape.c - 1) < 0.03, JSON.stringify(R.shape)));
  });

  test('왕복: 밝게(light, a 0.6)', () => {
    roundTrip(knownOp('light', 0.6, 0.7, 0.07, ELLIPSE), (R) => assert.deepEqual(R.shape, ELLIPSE));
  });

  test('신호가 너무 작으면 형태를 읽지 않는다(측정 불가 → 타원)', () => {
    const R = readShape(extractSamples(probe, render(knownOp('dark', 0.004, 0.97, 0.01, { p: 12, c: 1 }))));
    assert.ok(!R.measurable);
    assert.deepEqual(R.shape, ELLIPSE);
  });

  test('무편집 견본의 표본 이득은 0이다', () => {
    const S = extractSamples(probe, probe), g = gains(S, 'dark');
    assert.ok(S.m > 100000);
    assert.ok(g.every((v) => !Number.isFinite(v) || Math.abs(v) < 1e-12));
  });
});
