// 견본 JPG 바이트 → 레시피 JSON. 순서:
//   바이트 검사 → XMP 계획(디코드 전에 실패시킨다) → 디코드 → M1 읽기 → 홀드아웃(M1 · A2 · G1) → 분기 선택 → 고른 격자만 최종 맞춤 → 레시피
import { ExtractionFailure } from './failure.js';
import { loadIdentityProbe } from './grid/identity-probe.js';
import { fitSelected, scoreBranches, selectBranch } from './grid/select.js';
import { pickRgb, probeBase } from './grid/samples.js';
import { extractM1 } from './m1/extract.js';
import { EXTRACTOR_VERSION, PROBE_VERSION, encodeRecipe } from './recipe.js';
import { checkProbeBytes, decodeProbe } from './probe-input.js';
import { planFromXmp, readXmp } from './xmp.js';

const PROCESSING_MESSAGE = '필터를 만드는 중 문제가 생겼어요. 다시 올려 주세요.';

async function extract(buffer, opts) {
  checkProbeBytes(buffer, opts);
  const plan = planFromXmp(readXmp(buffer));
  let probe = await decodeProbe(buffer);
  const m1 = extractM1(probe, plan);
  const identity = await loadIdentityProbe();

  // 정답 표본만 뽑고 견본 전체 영상은 놓는다
  const base = probeBase(identity), tgt3 = pickRgb(probe.rgba, base);
  probe = null;

  const scored = scoreBranches(identity, base, m1, tgt3);
  const branch = selectBranch(scored.holdout);
  const grid = fitSelected(branch, scored, base);

  return {
    recipe: encodeRecipe({ branch, m1, grid }),
    meta: { branch, holdout: scored.holdout, warnings: [...plan.warnings], xmpSummary: plan.summary, extractorVersion: EXTRACTOR_VERSION, probeVersion: PROBE_VERSION },
  };
}

/**
 * @param {Buffer} buffer 작가가 올린 견본 JPG
 * @param {{ maxBytes?: number }} opts
 * @returns {Promise<{ recipe: object, meta: { branch, holdout: { M1, A2, G1 }, warnings: string[], xmpSummary, extractorVersion: string, probeVersion: number } }>}
 * @throws {ExtractionFailure} 작가 파일 문제는 INVALID_PROBE, 그 밖의 오류는 원인을 cause에 둔 PROCESSING_FAILED
 */
export async function extractRecipe(buffer, opts) {
  try {
    return await extract(buffer, opts);
  } catch (error) {
    if (error instanceof ExtractionFailure) throw error;
    const failure = ExtractionFailure.processingFailed(PROCESSING_MESSAGE);
    failure.cause = error;
    throw failure;
  }
}
