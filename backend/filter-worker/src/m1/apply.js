// M1 적용 스택(기준 렌더러): ① LUT → ③ 지역 톤 C → ④ 대역 이득 k_b → ⑤ 비네팅 V → ⑥ 그레인. 앱이 맞춰야 하는 결과가 이 출력이다.
// 연산자 식은 같은 폴더의 모듈(local-tone·detail·vignette·grain·color)을 그대로 부른다. 여기는 순서와 픽셀 단위 포장만 한다.
import { LIN, applyLut33, blurLogLum, encode, labPlanes, labToRgba, lstar } from './color.js';
import { bandGainDelta } from './detail.js';
import { applyGrain } from './grain.js';
import { toneDeltaAt } from './local-tone.js';
import { ELLIPSE, shapeDist, vignetteGainAt } from './vignette.js';

const ZERO = 1e-6; // L* 변화가 이보다 작은 픽셀은 입력 그대로 둔다(Lab 왕복 반올림이 값을 바꾸지 않게)

// L* 변화량 dL을 더해 sRGB로 되돌린다. 변화가 없는 픽셀은 입력 그대로
function addLightness(rgba, n, lab, dL) {
  const dst = new Uint8ClampedArray(n * 4);
  labToRgba(lab, n, dL, dst);
  for (let i = 0; i < n; i++) if (Math.abs(dL[i]) <= ZERO) for (let ch = 0; ch < 4; ch++) dst[i * 4 + ch] = rgba[i * 4 + ch];
  return dst;
}

// 지역 톤: 입력 L*와 주변 흐림 B에서 표를 읽어 L*에 더한다(a·b는 그대로). 흐림은 이 단계 입력(LUT 출력)에서 만든다
function applyTone(rgba, w, h, tone) {
  const n = w * h, lab = labPlanes(rgba, n), dL = new Float32Array(n);
  const B = tone.nb > 1 ? blurLogLum(rgba, w, h, tone.sigmaPct) : null;
  for (let i = 0; i < n; i++) dL[i] = toneDeltaAt(tone, lab.L[i], B ? B[i] : 0);
  return addLightness(rgba, n, lab, dL);
}

// 대역 이득. scaleK = 프레임 긴 변 / 원본 긴 변(대역 σ를 해상도에 맞춘다)
function applyBandGain(rgba, w, h, kb, scaleK) {
  const n = w * h, lab = labPlanes(rgba, n), dL = bandGainDelta(lab.L, w, h, kb, scaleK);
  if (dL.every((v) => Math.abs(v) <= ZERO)) return rgba;
  return addLightness(rgba, n, lab, dL);
}

// 비네팅: 선형광에서 어둡게(mode 'dark', 이득 exp(t)) 또는 흰색으로 섞는다('light')
function applyVignette(rgba, w, h, vig) {
  const dark = vig.mode !== 'light', shape = vig.shape || ELLIPSE, dst = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) {
    const yn = (y - (h - 1) / 2) / (h / 2);
    for (let x = 0; x < w; x++) {
      const p = (y * w + x) * 4, d = shapeDist((x - (w - 1) / 2) / (w / 2), yn, shape);
      const lr = LIN[rgba[p]], lg = LIN[rgba[p + 1]], lb = LIN[rgba[p + 2]];
      const t = vignetteGainAt(vig, d, lstar(0.2126729 * lr + 0.7151522 * lg + 0.072175 * lb));
      if (dark) { const g = Math.exp(t); dst[p] = encode(lr * g); dst[p + 1] = encode(lg * g); dst[p + 2] = encode(lb * g); }
      else { dst[p] = encode(lr + (1 - lr) * t); dst[p + 1] = encode(lg + (1 - lg) * t); dst[p + 2] = encode(lb + (1 - lb) * t); }
      dst[p + 3] = 255;
    }
  }
  return dst;
}

/**
 * 사진에 M1 레시피를 건다.
 * @param {Uint8ClampedArray} rgba 입력 사진 RGBA (width × height)
 * @param {{ lut: Uint8Array, V: object|null, C: object|null, K: object|null, grain: object|null }} m1 extractM1의 결과
 * @param {{ scaleK?: number, grain?: boolean }} opts scaleK: 프레임 긴 변 / 원본 긴 변, grain: false면 그레인을 건너뜀
 * @returns {Uint8ClampedArray} 새 RGBA
 */
export function applyM1(rgba, width, height, m1, { scaleK = 1, grain = true } = {}) {
  let img = applyLut33(rgba, m1.lut, new Uint8ClampedArray(width * height * 4));
  if (m1.C) img = applyTone(img, width, height, m1.C);
  if (m1.K) img = applyBandGain(img, width, height, m1.K, scaleK);
  if (m1.V) img = applyVignette(img, width, height, m1.V);
  if (grain && m1.grain) img = applyGrain(img, width, height, { ...m1.grain, seed: m1.grain.seed ?? 1 }, new Uint8ClampedArray(width * height * 4));
  return img;
}
