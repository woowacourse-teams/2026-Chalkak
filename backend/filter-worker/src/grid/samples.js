// 견본 표본: 무편집 견본과 편집본 견본에서 같은 위치(간격 4, 시작 2)를 뽑아 격자 입력·정답 짝을 만든다.
// 위치·반경·주변 밝기·무리(홀드아웃 체커)는 편집본과 무관해 한 번만 만든다(probeBase).
import { blurLogLum, deltaE2000, rgbToLab } from '../m1/color.js';
import { FOLD_BLOCKS, OFFSET, STRIDE, bNorm, gammaLum, predict as predictAt, radius } from './grid-core.js';

/**
 * @param {{ rgba: Uint8ClampedArray, width: number, height: number }} identity 무편집 견본
 * @returns {{ n, width, height, pos: Int32Array, r: Float32Array, B: Float32Array, folds: {sel, ref}, zraw: Uint8ClampedArray }}
 *   B: 표본 위치의 bNorm(무편집 입력의 1% 흐린 log 휘도), zraw: 표본 위치의 무편집 RGBA(n×4)
 */
export function probeBase({ rgba, width: W, height: H }) {
  const sw = Math.floor((W - OFFSET + STRIDE - 1) / STRIDE), sh = Math.floor((H - OFFSET + STRIDE - 1) / STRIDE), n = sw * sh;
  const pos = new Int32Array(n), r = new Float32Array(n), zraw = new Uint8ClampedArray(n * 4);
  const folds = Object.fromEntries(Object.keys(FOLD_BLOCKS).map((k) => [k, new Uint8Array(n)]));
  let s = 0;
  for (let j = 0; j < sh; j++) for (let i = 0; i < sw; i++, s++) {
    const x = OFFSET + i * STRIDE, y = OFFSET + j * STRIDE, p = y * W + x;
    pos[s] = p; r[s] = radius(x, y, W, H);
    for (const [k, block] of Object.entries(FOLD_BLOCKS)) folds[k][s] = (Math.floor(x / block) + Math.floor(y / block)) % 2;
    zraw.set(rgba.subarray(p * 4, p * 4 + 3), s * 4);
    zraw[s * 4 + 3] = 255;
  }
  const plane = blurLogLum(rgba, W, H, 0.01);
  const B = Float32Array.from(pos, (p) => bNorm(plane[p]));
  return { n, width: W, height: H, pos, r, B, folds, zraw };
}

// 전체 영상 RGBA에서 표본 위치의 RGB(n×3)
export function pickRgb(rgba, base) {
  const out = new Uint8Array(base.n * 3);
  for (let s = 0; s < base.n; s++) { const p = base.pos[s] * 4; out[s * 3] = rgba[p]; out[s * 3 + 1] = rgba[p + 1]; out[s * 3 + 2] = rgba[p + 2]; }
  return out;
}

// 격자 입력 z4(표본 n×4 RGBA)와 정답 tgt3(n×3)로 표본 묶음을 만든다. fold: 무리 번호 배열(base.folds.sel 또는 .ref)
export function gridSamples(spec, base, z4, tgt3, fold) {
  const D = spec.axes.length, n = base.n, q = new Float32Array(n * D), zin = new Uint8Array(n * 3);
  for (let s = 0; s < n; s++) {
    const lum = gammaLum(z4[s * 4], z4[s * 4 + 1], z4[s * 4 + 2]);
    zin[s * 3] = z4[s * 4]; zin[s * 3 + 1] = z4[s * 4 + 1]; zin[s * 3 + 2] = z4[s * 4 + 2];
    for (let d = 0; d < D; d++) { const a = spec.axes[d]; q[s * D + d] = a === 'r' ? base.r[s] : a === 'g' ? lum : base.B[s]; }
  }
  return { n, zin, tgt: tgt3, q, fold };
}

// 표본 예측. which(무리 번호)를 주면 그 무리 표본만 out3에 채운다(홀드아웃: 다른 무리로 맞춘 θ로 예측)
export function predictSamples(spec, th, S, which, out3 = new Uint8Array(S.n * 3)) {
  const D = spec.axes.length, idx = new Int32Array(1 << D), wt = new Float64Array(1 << D), q = new Float64Array(D), px = [0, 0, 0];
  for (let s = 0; s < S.n; s++) {
    if (which !== null && S.fold[s] !== which) continue;
    for (let d = 0; d < D; d++) q[d] = S.q[s * D + d];
    predictAt(spec, th, S.zin[s * 3], S.zin[s * 3 + 1], S.zin[s * 3 + 2], q, idx, wt, px);
    out3[s * 3] = px[0]; out3[s * 3 + 1] = px[1]; out3[s * 3 + 2] = px[2];
  }
  return out3;
}

// 표본 RGB 두 묶음(n×3)의 ΔE2000 평균. include(s)가 있으면 그 표본만
export function meanSampleDeltaE(a3, b3, n, include = null) {
  const la = new Float64Array(3), lb = new Float64Array(3);
  let sum = 0, cnt = 0;
  for (let s = 0; s < n; s++) {
    if (include && !include(s)) continue;
    rgbToLab(a3[s * 3], a3[s * 3 + 1], a3[s * 3 + 2], la, 0);
    rgbToLab(b3[s * 3], b3[s * 3 + 1], b3[s * 3 + 2], lb, 0);
    sum += deltaE2000(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
    cnt++;
  }
  return sum / cnt;
}
