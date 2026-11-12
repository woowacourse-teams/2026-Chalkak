// 분기 선택(연구 e15 G3와 같은 규칙): M1 · A2 · G1 가운데 견본 안 홀드아웃이 가장 낮은 쪽을 기준으로 단순한 쪽을 고른다.
import { ExtractionFailure } from '../failure.js';
import { A2_SPEC, G1_SPEC, accumulate, solve } from './grid-core.js';
import { a2Input, fitGrid, g1Input } from './fit.js';
import { gridSamples, meanSampleDeltaE, predictSamples } from './samples.js';

export const NEAR_TOLERANCE = 0.05; // 최저값과 이 안이면 더 단순한 쪽을 고른다(ΔE2000)

/**
 * 격자 후보의 홀드아웃 점수: 한 무리로 맞추고 다른 무리를 예측하는 것을 두 방향으로 해서 전 표본의 평균 ΔE2000.
 * S.fold는 선택용 1024px 체커(base.folds.sel)여야 한다. 쌓은 정규 방정식(acc)도 돌려줘서 같은 무리로 하는 최종 맞춤에 재사용한다.
 */
export function holdoutScore(spec, S) {
  const acc = accumulate(spec, S), out3 = new Uint8Array(S.n * 3);
  predictSamples(spec, solve(spec, acc, [1]), S, 0, out3);
  predictSamples(spec, solve(spec, acc, [0]), S, 1, out3);
  return { score: meanSampleDeltaE(out3, S.tgt, S.n), acc };
}

/**
 * M1이 최저값과 0.05 이내면 M1, 아니면 최저값과 0.05 이내인 것 중 A2, G1 순.
 * @param {{ M1: number, A2: number, G1: number }} scores 낮을수록 좋다
 * @returns {'M1'|'A2'|'G1'}
 */
export function selectBranch(scores) {
  const best = Math.min(scores.M1, scores.A2, scores.G1);
  if (scores.M1 - best <= NEAR_TOLERANCE) return 'M1';
  const branch = ['A2', 'G1'].find((k) => scores[k] - best <= NEAR_TOLERANCE);
  if (!branch) throw ExtractionFailure.processingFailed('분기를 고를 수 없어요 (홀드아웃 점수가 숫자가 아니에요).');
  return branch;
}

/**
 * 세 분기의 점수를 낸다. M1 점수는 홀드아웃이 아니다: M1은 견본 전체에서 읽은 값이라 전 표본에서 M1 출력(무편집 견본에 건 것, scaleK 1, 그레인 없음)과
 * 정답의 평균 ΔE다(연구 e15와 같다). 전체 해상도 M1 적용은 여기서 한 번만 한다(G1 표본이 그 출력이다).
 * @param {object} identity 무편집 견본
 * @param {object} base probeBase(identity)
 * @param {object} m1 extractM1의 결과
 * @param {Uint8Array} tgt3 편집본 견본의 표본 RGB (pickRgb)
 * @param {{ fold?: Uint8Array }} opts fold: 홀드아웃 무리 번호 배열(기본 base.folds.sel = 1024px 체커). 견본보다 작은 합성 장면을 시험할 때 바꾼다
 * @returns {{ holdout: { M1, A2, G1 }, a2: object, g1: object, g1Acc: object }} a2·g1: 선택용(1024px) 무리의 표본 묶음, g1Acc: G1 정규 방정식
 */
export function scoreBranches(identity, base, m1, tgt3, { fold = base.folds.sel } = {}) {
  const g1 = gridSamples(G1_SPEC, base, g1Input(identity, m1, base), tgt3, fold);
  const a2 = gridSamples(A2_SPEC, base, a2Input(m1, base), tgt3, fold);
  const a2Hold = holdoutScore(A2_SPEC, a2), g1Hold = holdoutScore(G1_SPEC, g1);
  return { holdout: { M1: meanSampleDeltaE(g1.zin, tgt3, base.n), A2: a2Hold.score, G1: g1Hold.score }, a2, g1, g1Acc: g1Hold.acc };
}

/**
 * 고른 분기의 최종 격자를 맞춘다(M1이면 null). 점수 때 만든 것을 재사용하되 θ는 연구 맞춤과 같은 무리 나눔으로 쌓는다:
 * A2는 256px 체커(연구 grids.json과 같은 비트), G1은 점수 때 쌓은 1024px 체커 그대로.
 */
export function fitSelected(branch, scored, base) {
  if (branch === 'A2') {
    const S = { ...scored.a2, fold: base.folds.ref };
    return fitGrid(A2_SPEC, S, accumulate(A2_SPEC, S));
  }
  return branch === 'G1' ? fitGrid(G1_SPEC, scored.g1, scored.g1Acc) : null;
}
