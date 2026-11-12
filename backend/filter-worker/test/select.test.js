// 분기 선택: 규칙(연구 e15 G3) 단위 시험과 합성 장면의 홀드아웃. 견본 크기에 묶이지 않는 기계라 합성 장면(384×512)으로 본다.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { A2_SPEC, G1_SPEC } from '../src/grid/grid-core.js';
import { fitGrid } from '../src/grid/fit.js';
import { fitSelected, holdoutScore, scoreBranches, selectBranch } from '../src/grid/select.js';
import { pickRgb, probeBase } from '../src/grid/samples.js';
import { fitA2, fitG1, a2Samples, g1Samples } from '../src/grid/fit.js';
import { applyStack, vignetteFromSeparable } from './support/apply-stack.js';
import { curvedLut } from './support/grid-fixtures.js';
import { makeScene } from './support/scenes.js';

const SW = 384, SH = 512;
const frame = (rgba) => ({ rgba, width: SW, height: SH });
const recipe = (lut, V = null) => ({ lut, V, C: null, K: null, grain: null });
const VIG = vignetteFromSeparable([0, -0.1, -0.25, -0.45, -0.7, -0.9, -1.1, -1.3], [1, 1, 1, 1, 1, 1, 1, 1], 'dark');

describe('선택 규칙', () => {
  test('M1이 최저값과 정확히 0.05 차이면 M1, 그보다 크면 격자', () => {
    assert.equal(0.1 - 0.05, 0.05); // 경계가 실수 오차 없이 정확히 0.05인 짝
    assert.equal(selectBranch({ M1: 0.1, A2: 0.05, G1: 1 }), 'M1');
    assert.equal(selectBranch({ M1: 0.1000001, A2: 0.05, G1: 1 }), 'A2');
  });

  test('M1이 최저면 M1', () => {
    assert.equal(selectBranch({ M1: 0.5, A2: 0.9, G1: 0.8 }), 'M1');
  });

  test('A2와 G1이 둘 다 최저값 0.05 이내면 A2가 먼저', () => {
    assert.equal(selectBranch({ M1: 2, A2: 1.04, G1: 1 }), 'A2');
    assert.equal(selectBranch({ M1: 2, A2: 1, G1: 1 }), 'A2');
  });

  test('G1이 최저값이고 A2가 0.05보다 멀면 G1', () => {
    assert.equal(selectBranch({ M1: 2, A2: 1.0500001, G1: 1 }), 'G1');
    assert.equal(selectBranch({ M1: 2, A2: 1.5, G1: 1 }), 'G1');
  });

  test('점수가 숫자가 아니면 처리 실패', () => {
    assert.throws(() => selectBranch({ M1: NaN, A2: NaN, G1: NaN }), { type: 'PROCESSING_FAILED' });
  });
});

describe('합성 장면 홀드아웃', () => {
  const scene = makeScene(0, SW, SH), identity = frame(scene), base = probeBase(identity);
  const lut = curvedLut();
  const edited = frame(applyStack(scene, SW, SH, { lut, V: VIG }, { V: true }));
  const tgt3 = pickRgb(edited.rgba, base);
  // 1024px 체커는 384×512 장면에서 한 무리뿐이라, 같은 모양의 64px 체커를 쓴다
  const fold = Uint8Array.from(base.pos, (p) => (Math.floor((p % SW) / 64) + Math.floor(Math.floor(p / SW) / 64)) % 2);
  const score = (m1) => scoreBranches(identity, base, m1, tgt3, { fold });

  test('M1이 못 읽은 비네팅이 있으면 A2 또는 G1이 M1보다 훨씬 낮고 격자를 고른다', () => {
    const { holdout } = score(recipe(lut));
    assert.ok(holdout.M1 > 1, `M1 ${holdout.M1}`);
    assert.ok(Math.min(holdout.A2, holdout.G1) < holdout.M1 * 0.5, JSON.stringify(holdout));
    assert.ok(holdout.A2 < holdout.M1 * 0.5, `A2 ${holdout.A2} (M1 ${holdout.M1})`);
    assert.equal(selectBranch(holdout), 'A2');
  });

  test('M1이 이미 맞으면 M1을 고른다', () => {
    const { holdout } = score(recipe(lut, VIG));
    assert.ok(holdout.M1 < 0.2, `M1 ${holdout.M1}`);
    assert.equal(selectBranch(holdout), 'M1');
  });

  test('홀드아웃 점수는 scoreBranches가 내는 값과 같다 (두 무리 방향 평균)', () => {
    const { holdout, a2, g1 } = score(recipe(lut));
    assert.equal(holdoutScore(A2_SPEC, a2).score, holdout.A2);
    assert.equal(holdoutScore(G1_SPEC, g1).score, holdout.G1);
  });

  test('점수 때 쌓은 표본으로 한 최종 맞춤이 기존 fitA2·fitG1과 비트까지 같다', () => {
    const m1 = recipe(lut), scored = score(m1);
    // fitSelected는 점수용(1024px) 표본을 재사용하되 A2는 256px 체커로 다시 쌓는다
    assert.deepEqual(fitSelected('A2', scored, base).theta, fitA2(identity, edited, m1, { base }).theta);
    assert.deepEqual(fitSelected('G1', scored, base), fitGrid(G1_SPEC, scored.g1)); // 점수 때 쌓은 정규 방정식을 다시 쌓지 않고 쓴 것과 같다
    assert.equal(fitSelected('M1', scored, base), null);
    assert.deepEqual(fitA2(identity, edited, m1, { base, samples: a2Samples(identity, edited, m1, base) }).theta, fitA2(identity, edited, m1, { base }).theta);
    assert.deepEqual(fitG1(identity, edited, m1, { base, samples: g1Samples(identity, edited, m1, base) }).theta, fitG1(identity, edited, m1, { base }).theta);
  });
});
