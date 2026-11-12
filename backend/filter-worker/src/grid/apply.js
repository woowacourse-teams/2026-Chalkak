// 격자 분기 적용: 사진 한 장에 A2 또는 G1 θ를 건다. 주변 밝기 축은 항상 "지금 렌더하는 사진의 무편집 입력"에서 흐린다.
import { applyLut33, blurLogLum } from '../m1/color.js';
import { A2_SPEC, G1_SPEC, bNorm, gammaLum, predict, radius } from './grid-core.js';

// zin = 격자 입력(A2: LUT 출력, G1: M1 출력), raw = 무편집 입력. 둘 다 width × height RGBA
function applyGrid(spec, theta, zin, raw, width, height) {
  const D = spec.axes.length, Bp = blurLogLum(raw, width, height, spec.pct);
  const out = new Uint8ClampedArray(width * height * 4), idx = new Int32Array(1 << D), wt = new Float64Array(1 << D), q = new Float64Array(D), px = [0, 0, 0];
  for (let y = 0; y < height; y++) for (let x = 0; x < width; x++) {
    const i = y * width + x, p = i * 4;
    for (let d = 0; d < D; d++) { const a = spec.axes[d]; q[d] = a === 'r' ? radius(x, y, width, height) : a === 'g' ? gammaLum(zin[p], zin[p + 1], zin[p + 2]) : bNorm(Bp[i]); }
    predict(spec, theta, zin[p], zin[p + 1], zin[p + 2], q, idx, wt, px);
    out[p] = px[0]; out[p + 1] = px[1]; out[p + 2] = px[2]; out[p + 3] = 255;
  }
  return out;
}

/** A2: 무편집 사진 rgba에 M1 LUT를 걸고 격자를 건다 */
export const applyA2 = (rgba, width, height, lut, theta) => applyGrid(A2_SPEC, theta, applyLut33(rgba, lut, new Uint8ClampedArray(width * height * 4)), rgba, width, height);

/** G1: m1Rgba = 같은 사진에 applyM1(그레인 없이)을 건 결과, rawRgba = 무편집 사진 */
export const applyG1 = (m1Rgba, rawRgba, width, height, theta) => applyGrid(G1_SPEC, theta, m1Rgba, rawRgba, width, height);
