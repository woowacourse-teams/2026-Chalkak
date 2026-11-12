// 격자 분기(A2·G1) 맞춤·적용. 식은 연구 코드와 같아서 연구 자가 시험을 그대로 옮긴다: 무편집 → 무편집은 항등이어야 한다.
// 견본 크기에 묶이지 않는 기계라 합성 장면(384×512)으로 시험한다.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyA2, applyG1 } from '../src/grid/apply.js';
import { fitA2, fitG1, a2Samples, g1Samples } from '../src/grid/fit.js';
import { A2_SPEC, G1_SPEC, accumulate, nParams, solve } from '../src/grid/grid-core.js';
import { loadIdentityProbe } from '../src/grid/identity-probe.js';
import { meanSampleDeltaE, pickRgb, predictSamples, probeBase } from '../src/grid/samples.js';
import { applyM1 } from '../src/m1/apply.js';
import { applyLut33 } from '../src/m1/color.js';
import { applyStack, vignetteFromSeparable } from './support/apply-stack.js';
import { curvedLut } from './support/grid-fixtures.js';
import { identityLut } from './support/probe-edit.js';
import { makeScene } from './support/scenes.js';

const SW = 384, SH = 512;
const frame = (rgba) => ({ rgba, width: SW, height: SH });
const recipe = (lut, V = null) => ({ lut, V, C: null, K: null, grain: null });
const VIG = vignetteFromSeparable([0, -0.1, -0.25, -0.45, -0.7, -0.9, -1.1, -1.3], [1, 1, 1, 1, 1, 1, 1, 1], 'dark');
// 항등과 θ의 최대 차: 꼭짓점마다 [R,G,B,1] → 출력 채널 대각이 1
function maxIdentityDeviation(theta) {
  let dev = 0;
  for (let v = 0; v < theta.length / 12; v++) for (let i = 0; i < 4; i++) for (let ch = 0; ch < 3; ch++) dev = Math.max(dev, Math.abs(theta[(v * 4 + i) * 3 + ch] - (i === ch ? 1 : 0)));
  return dev;
}

describe('격자 분기', () => {
  const scene = makeScene(0, SW, SH), identity = frame(scene), base = probeBase(identity);

  test('배포 자산: 무편집 견본이 3072×4096로 디코드되고 한 번만 읽는다', async () => {
    const a = await loadIdentityProbe(), b = await loadIdentityProbe();
    assert.equal(a, b);
    assert.equal(a.width * a.height * 4, a.rgba.length);
    assert.deepEqual([a.width, a.height], [3072, 4096]);
  });

  test('꼴: A2는 [5,9,7,4,3], G1은 [3,5,5,4,3]', () => {
    const lut = recipe(identityLut());
    const a = fitA2(identity, identity, lut, { base }), g = fitG1(identity, identity, lut, { base });
    assert.deepEqual(a.dims, [5, 9, 7, 4, 3]);
    assert.deepEqual(g.dims, [3, 5, 5, 4, 3]);
    assert.equal(a.theta.length, 5 * 9 * 7 * 12);
    assert.equal(g.theta.length, 3 * 5 * 5 * 12);
  });

  test('무편집 → 무편집: 두 분기 모두 항등에 가깝고 표본 ΔE가 0에 가깝다', () => {
    const m1 = recipe(identityLut());
    const a = fitA2(identity, identity, m1, { base }), g = fitG1(identity, identity, m1, { base });
    assert.ok(maxIdentityDeviation(a.theta) < 0.05, `A2 항등과 최대 차 ${maxIdentityDeviation(a.theta)}`);
    assert.ok(maxIdentityDeviation(g.theta) < 0.05, `G1 항등과 최대 차 ${maxIdentityDeviation(g.theta)}`);
    for (const [label, spec, S, th] of [['A2', A2_SPEC, a2Samples(identity, identity, m1, base), a.theta], ['G1', G1_SPEC, g1Samples(identity, identity, m1, base), g.theta]]) {
      const dE = meanSampleDeltaE(predictSamples(spec, th, S, null), S.tgt, S.n);
      assert.ok(dE < 0.05, `${label} 표본 ΔE ${dE}`);
    }
  });

  test('LUT가 못 읽은 비네팅을 A2가 줄인다 (LUT만 대비 표본 ΔE)', () => {
    const lut = curvedLut(), edited = frame(applyStack(scene, SW, SH, { lut, V: VIG }, { V: true })), m1 = recipe(lut);
    const S = a2Samples(identity, edited, m1, base), tgt = pickRgb(edited.rgba, base);
    const lutOnly = meanSampleDeltaE(pickRgb(applyLut33(scene, lut, new Uint8ClampedArray(scene.length)), base), tgt, base.n);
    const { theta } = fitA2(identity, edited, m1, { base });
    const fitted = meanSampleDeltaE(predictSamples(A2_SPEC, theta, S, null), tgt, base.n);
    assert.ok(lutOnly > 1, `LUT만 ΔE ${lutOnly}`);
    assert.ok(fitted < lutOnly * 0.5, `A2 ΔE ${fitted} (LUT만 ${lutOnly})`);
  });

  test('G1: M1이 못 읽은 비네팅은 줄이고, M1이 이미 맞으면 M1 그대로 둔다', () => {
    const lut = curvedLut(), edited = frame(applyStack(scene, SW, SH, { lut, V: VIG }, { V: true })), tgt = pickRgb(edited.rgba, base);
    const err = (m1) => {
      const S = g1Samples(identity, edited, m1, base), before = meanSampleDeltaE(S.zin, tgt, base.n);
      return [before, meanSampleDeltaE(predictSamples(G1_SPEC, fitG1(identity, edited, m1, { base }).theta, S, null), tgt, base.n)];
    };
    const [b0, a0] = err(recipe(lut));
    assert.ok(a0 < b0 * 0.5, `G1 ${a0} (M1 ${b0})`);
    const [b1, a1] = err(recipe(lut, VIG));
    assert.ok(a1 <= b1 + 0.05, `맞는 M1에서 G1 ${a1} (M1 ${b1})`);
  });

  test('적용: 결정적이고, 표본 위치에서 맞춤 예측과 같다', () => {
    const lut = curvedLut(), edited = frame(applyStack(scene, SW, SH, { lut, V: VIG }, { V: true })), m1 = recipe(lut);
    const a2 = fitA2(identity, edited, m1, { base }).theta, g1 = fitG1(identity, edited, m1, { base }).theta;
    const outA = applyA2(scene, SW, SH, lut, a2), m1Img = applyM1(scene, SW, SH, m1, { grain: false }), outG = applyG1(m1Img, scene, SW, SH, g1);
    assert.deepEqual(applyA2(scene, SW, SH, lut, a2), outA);
    assert.deepEqual(applyG1(m1Img, scene, SW, SH, g1), outG);
    assert.deepEqual(pickRgb(outA, base), predictSamples(A2_SPEC, a2, a2Samples(identity, edited, m1, base), null));
    assert.deepEqual(pickRgb(outG, base), predictSamples(G1_SPEC, g1, g1Samples(identity, edited, m1, base), null));
    assert.equal(outA[3], 255);
  });

  test('자산을 못 읽으면 작가 탓이 아닌 내부 오류', async () => {
    for (const source of ['/nonexistent/identity.jpg', new URL('../package.json', import.meta.url)]) {
      await assert.rejects(loadIdentityProbe(source), { type: 'PROCESSING_FAILED', message: '내장 무편집 견본을 읽을 수 없어요.' });
    }
  });

  test('정규 행렬이 양의 정부호가 아니면 처리 실패', () => {
    const P = nParams(G1_SPEC), H = new Float64Array(P * P);
    for (let i = 0; i < P; i++) H[i * P + i] = -1;
    const acc = { H: [H, new Float64Array(P * P)], R: [new Float64Array(P * 3), new Float64Array(P * 3)], cnt: [1, 0] };
    assert.throws(() => solve(G1_SPEC, acc, [0]), { type: 'PROCESSING_FAILED' });
  });

  test('크기가 다른 견본 쌍은 처리 실패', () => {
    assert.throws(() => fitA2(identity, { rgba: new Uint8ClampedArray(16), width: 2, height: 2 }, recipe(identityLut()), { base }), { type: 'PROCESSING_FAILED' });
  });
});
