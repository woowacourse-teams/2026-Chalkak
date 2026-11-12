// 격자 분기 맞춤: A2(LUT 출력 → 격자)와 G1(M1 출력 → 잔차 격자). 입력은 무편집 견본과 편집본 견본, 그리고 그 견본에서 읽은 M1 레시피.
// 어느 분기를 쓸지 고르는 일은 여기 없다.
import { ExtractionFailure } from '../failure.js';
import { applyLut33 } from '../m1/color.js';
import { applyM1 } from '../m1/apply.js';
import { A2_SPEC, G1_SPEC, accumulate, gridDims, solve } from './grid-core.js';
import { gridSamples, pickRgb, probeBase } from './samples.js';

function checkPair(identity, probe) {
  if (identity.width !== probe.width || identity.height !== probe.height) {
    throw ExtractionFailure.processingFailed('내부 견본 크기가 맞지 않아요.');
  }
}

/** A2 격자 입력: M1 LUT를 무편집 견본 표본에 건 값(표본 n×4 RGBA) */
export const a2Input = (m1, base) => applyLut33(base.zraw, m1.lut, new Uint8ClampedArray(base.n * 4));

/** G1 격자 입력: 무편집 견본 전체에 M1 스택(그레인 없이, 원본 해상도)을 건 영상의 표본(n×4 RGBA). 전체 해상도 적용이라 한 번만 부른다 */
export function g1Input(identity, m1, base) {
  const m1Full = applyM1(identity.rgba, identity.width, identity.height, m1, { scaleK: 1, grain: false });
  const z4 = new Uint8ClampedArray(base.n * 4);
  for (let s = 0; s < base.n; s++) { const p = base.pos[s] * 4; z4[s * 4] = m1Full[p]; z4[s * 4 + 1] = m1Full[p + 1]; z4[s * 4 + 2] = m1Full[p + 2]; z4[s * 4 + 3] = 255; }
  return z4;
}

/** A2 표본: 격자 입력 = a2Input, 정답 = 편집본 견본의 같은 위치. 'ref'(256px 체커) 무리 */
export function a2Samples(identity, probe, m1, base = probeBase(identity)) {
  checkPair(identity, probe);
  return gridSamples(A2_SPEC, base, a2Input(m1, base), pickRgb(probe.rgba, base), base.folds.ref);
}

/** G1 표본: 격자 입력 = g1Input. 'sel'(1024px 체커) 무리 */
export function g1Samples(identity, probe, m1, base = probeBase(identity)) {
  checkPair(identity, probe);
  return gridSamples(G1_SPEC, base, g1Input(identity, m1, base), pickRgb(probe.rgba, base), base.folds.sel);
}

/** 표본 묶음 S(무리 0·1 전부)로 θ를 푼다. acc를 이미 쌓았으면 다시 쌓지 않는다 */
export const fitGrid = (spec, S, acc = accumulate(spec, S)) => ({ dims: gridDims(spec), theta: solve(spec, acc, [0, 1]) });

/**
 * @param {{ rgba, width, height }} identity 무편집 견본(배포 자산)
 * @param {{ rgba, width, height }} probe 편집본 견본
 * @param {object} m1 extractM1(probe)의 결과
 * @param {{ base?: object, samples?: object }} opts base: 두 분기가 같은 probeBase(identity)를 나눠 쓸 때, samples: 미리 만든 표본 묶음
 * @returns {{ dims: number[], theta: Float64Array }} dims = [...축별 꼭짓점 수, 4, 3]
 */
export const fitA2 = (identity, probe, m1, { base, samples } = {}) => fitGrid(A2_SPEC, samples ?? a2Samples(identity, probe, m1, base));
export const fitG1 = (identity, probe, m1, { base, samples } = {}) => fitGrid(G1_SPEC, samples ?? g1Samples(identity, probe, m1, base));
