// M1 레시피 추출: 디코드한 견본 이미지와 XMP 계획에서 { lut, V, C, K, grain }을 읽는다.
//   ① 마커 정합 → ② 비네팅 V ↔ 33³ LUT 번갈아 읽기(VIG_PASSES번) → ③ 지역 톤 C와 디테일 k_b(둘 다 켜지면 JOINT_ITERS번 번갈아 맞춤) → ④ 그레인
// 켜기·끄기는 XMP 계획(plan.on)이 정한다. 꺼진 연산자는 읽지 않고 null로 둔다.
// 이미지 한 장이 RGBA 50MB라 큰 중간 결과는 단계가 끝나는 대로 놓고, 평면은 한 번에 하나만 둔다(워커 메모리 1GB 기준).
import { applyLut33, lstarPlane, blurLogLum } from './color.js';
import { extractCoreLut } from './core-lut.js';
import { bandGainDelta, fitBandGain, KIND_AMP0, regionPlanes } from './detail.js';
import { H, W, clearProbeCaches, inputProbe, registerMarkers } from './layout.js';
import { C_SIGMAS, bestOf, cSamples, fitC, toneDeltaAt } from './local-tone.js';
import { correctProbeVignette, fitProbeVignette } from './probe-vignette.js';
import { clearVignetteCaches, extractSamples, invertVignette, readShape } from './vignette.js';
import { grainFromFlats, measureFlats } from './grain.js';

const VIG_PASSES = 2; // 비네팅 ↔ LUT 번갈아 읽기 횟수 (합성 시험에서 2번째 이후 LUT 변화가 0.05 ΔE 아래)
const JOINT_ITERS = 4; // C와 k_b 번갈아 맞춤 횟수

// 비네팅 ↔ LUT 번갈아 읽기. 반환: { lut, V, corr(비네팅을 되돌린 견본), buf(재사용할 RGBA 버퍼) }
function readVignetteAndLut(rgba) {
  let first = correctProbeVignette(rgba, fitProbeVignette(rgba));
  let lut = extractCoreLut(first);
  first = null;
  const inp = inputProbe(), ref = new Uint8ClampedArray(inp.length);
  let vig = null, corr = null;
  for (let pass = 0; pass < VIG_PASSES; pass++) {
    corr = null; // 이전 패스의 되돌린 견본은 새로 만들기 전에 놓는다
    vig = readShape(extractSamples(applyLut33(inp, lut, ref), rgba)).vig;
    corr = invertVignette(rgba, W, H, vig);
    lut = extractCoreLut(corr);
  }
  clearVignetteCaches();
  return { lut, V: vig, corr, buf: ref };
}

// C 표를 건 입력 L*: 영역 평면(여백 포함)에 C를 더한다 (스택 순서 LUT → C → k_b)
const withTone = (ins, tone, Bf) => ins.map((p) => ({
  ...p,
  L: Float32Array.from(p.L, (v, i) => {
    const x = p.x0 + (i % p.w), y = p.y0 + Math.floor(i / p.w);
    return Math.min(100, Math.max(0, v + toneDeltaAt(tone, v, Bf[y * W + x])));
  }),
}));

// C와 k_b를 번갈아 맞춘다(backfitting): C는 R − k_b 몫에, k_b는 C를 건 입력에 맞춘다. 대역 연산만 있는 견본이면 C가 0으로 간다
function fitJoint(smp, Cs0, pre, ins, outs) {
  // 큰 평면 둘(주변 흐림, 입력 L*)은 한 번만 만들어 되풀이해 쓴다
  const Bf = new Float32Array(W * H), Lc = new Float32Array(W * H);
  let blurK = -1;
  const blurAt = (k) => {
    if (blurK !== k) { blurLogLum(pre, W, H, C_SIGMAS[k], Bf); blurK = k; }
    return Bf;
  };
  let best = bestOf(Cs0), tone = Cs0[best].tone;
  let K = fitBandGain(withTone(ins, tone, blurAt(best)), outs, KIND_AMP0);
  for (let it = 1; it < JOINT_ITERS; it++) {
    lstarPlane(pre, W * H, Lc);
    const B = blurAt(best);
    for (let i = 0; i < Lc.length; i++) Lc[i] = Math.min(100, Math.max(0, Lc[i] + toneDeltaAt(tone, Lc[i], B[i])));
    const dK = bandGainDelta(Lc, W, H, K, 1, smp.idx);
    const Cs = fitC({ ...smp, R: Float32Array.from(smp.R, (v, s) => v - dK[s]) });
    best = bestOf(Cs); tone = Cs[best].tone;
    K = fitBandGain(withTone(ins, tone, blurAt(best)), outs, KIND_AMP0);
  }
  return { C: tone, K };
}

/**
 * 견본 이미지에서 M1 레시피를 읽는다.
 * @param {{ rgba: Uint8ClampedArray, width: number, height: number }} probe 디코드한 견본 (3072x4096 sRGB RGBA)
 * @param {{ on: { V: boolean, C: boolean, K: boolean, grain: boolean } }} plan planFromXmp의 결과
 * @returns {{ lut: Uint8Array, V: object|null, C: object|null, K: object|null, grain: object|null }}
 *   lut: 33³ × RGB 8비트(r이 가장 빨리 변하는 순서), 꺼진 연산자는 null.
 * @throws {ExtractionFailure} 크기가 다르거나 마커가 어긋나면 INVALID_PROBE
 */
export function extractM1({ rgba, width, height }, plan) {
  const on = plan.on;
  try {
    registerMarkers(rgba, width, height);

    let lut, V = null, corr = rgba, buf = null;
    if (on.V) ({ lut, V, corr, buf } = readVignetteAndLut(rgba));
    else lut = extractCoreLut(rgba);

    let C = null, K = null;
    if (on.C || on.K) {
      const pre = applyLut33(inputProbe(), lut, buf ?? new Uint8ClampedArray(rgba.length));
      clearProbeCaches();
      const ins = regionPlanes(pre), outs = regionPlanes(corr);
      if (on.C) {
        const smp = cSamples(corr, pre), Cs0 = fitC(smp);
        corr = null; // 이 뒤로는 되돌린 견본 전체가 필요 없다(영역 평면은 이미 잘라 뒀다)
        if (on.K) ({ C, K } = fitJoint(smp, Cs0, pre, ins, outs));
        else C = Cs0[bestOf(Cs0)].tone;
      } else K = fitBandGain(ins, outs, KIND_AMP0);
    }

    const grain = on.grain ? grainFromFlats(measureFlats(rgba, width)) : null;
    return { lut, V, C, K, grain };
  } finally {
    clearProbeCaches();
    clearVignetteCaches();
  }
}

// 레시피를 JSON으로 쓸 수 있는 평범한 객체로 바꾼다(연구 U5.toJson과 같은 모양: 형식화 배열 → 배열)
export function toRecipeJson(m1) {
  return {
    lut: Array.from(m1.lut),
    V: m1.V && { ...m1.V, T: Array.from(m1.V.T) },
    C: m1.C && { ...m1.C, T: Array.from(m1.C.T) },
    K: m1.K && { sigmas: m1.K.sigmas, eKnots: m1.K.eKnots, mKnots: m1.K.mKnots, K: Array.from(m1.K.K) },
    grain: m1.grain && { ...m1.grain, amp: Array.from(m1.grain.amp) },
  };
}
