// 시험용 적용 스택: 알려진 연산을 견본·장면에 걸어 합성 "편집본"을 만들고, 읽은 레시피를 다시 걸어 비교한다.
// 읽기 코드가 부르지 않는 적용 쪽 연산자(LUT 뒤 C → k_b → 비네팅 → 그레인 순서)만 여기 둔다. 식은 연구 코드와 같다.
import { LIN, applyLut33, blurLogLum, encode, labPlanes, labToRgba, lstar } from '../../src/m1/color.js';
import { bandGainDelta } from '../../src/m1/detail.js';
import { applyGrain } from '../../src/m1/grain.js';
import { toneDeltaAt } from '../../src/m1/local-tone.js';
import { ELLIPSE, shapeDist, vignetteGainAt } from '../../src/m1/vignette.js';

const TONE_ZERO = 1e-6;
const ZERO = 1e-6;

// 분리형 비네팅 표: T = u[i] · s[k]. 'dark'는 ln 이득(≤ 0), 'light'는 흰색 섞음(0~1)
export function vignetteFromSeparable(u, s, mode, vKnots = [10, 22, 34, 46, 58, 70, 82, 94]) {
  const nr = u.length, nv = s.length, T = new Float64Array(nv * nr);
  for (let k = 0; k < nv; k++) for (let i = 0; i < nr; i++) {
    const t = u[i] * s[k];
    T[k * nr + i] = mode === 'light' ? Math.min(1, Math.max(0, t)) : Math.min(0, t);
  }
  return { nr, nv, vKnots, mode, T };
}

// 지역 톤: 입력 L*에 변화량을 더하고 a·b는 그대로 둔다. 변화량이 0인 픽셀은 입력 그대로
export function applyTone(rgba, w, h, tone, dst) {
  const n = w * h, lab = labPlanes(rgba, n), B = tone.nb > 1 ? blurLogLum(rgba, w, h, tone.sigmaPct) : null, dL = new Float32Array(n);
  let any = false;
  for (let i = 0; i < n; i++) { dL[i] = toneDeltaAt(tone, lab.L[i], B ? B[i] : 0); if (Math.abs(dL[i]) > TONE_ZERO) any = true; }
  if (!any) { dst.set(rgba); return dst; }
  labToRgba(lab, n, dL, dst);
  for (let i = 0; i < n; i++) if (Math.abs(dL[i]) <= TONE_ZERO) for (let ch = 0; ch < 4; ch++) dst[i * 4 + ch] = rgba[i * 4 + ch];
  return dst;
}

// 대역 이득. 바뀌지 않는 픽셀(|ΔL*| ≤ 1e-6)은 입력 그대로 둔다
export function applyBandGain(rgba, w, h, kb, dst, { scale = 1 } = {}) {
  const n = w * h, lab = labPlanes(rgba, n), dL = bandGainDelta(lab.L, w, h, kb, scale);
  if (dL.every((v) => Math.abs(v) <= ZERO)) { dst.set(rgba); return dst; }
  labToRgba(lab, n, dL, dst);
  for (let i = 0; i < n; i++) if (Math.abs(dL[i]) <= ZERO) for (let ch = 0; ch < 4; ch++) dst[i * 4 + ch] = rgba[i * 4 + ch];
  return dst;
}

// 비네팅. (x0, y0, FW, FH)는 잘라낸 영역의 전체 프레임 좌표
export function applyVignette(rgba, w, h, vig, dst, { x0 = 0, y0 = 0, FW = w, FH = h } = {}) {
  const dark = vig.mode !== 'light', shape = vig.shape || ELLIPSE;
  for (let y = 0; y < h; y++) {
    const yn = (y + y0 - (FH - 1) / 2) / (FH / 2);
    for (let x = 0; x < w; x++) {
      const p = (y * w + x) * 4, d = shapeDist((x + x0 - (FW - 1) / 2) / (FW / 2), yn, shape);
      const lr = LIN[rgba[p]], lg = LIN[rgba[p + 1]], lb = LIN[rgba[p + 2]], t = vignetteGainAt(vig, d, lstar(0.2126729 * lr + 0.7151522 * lg + 0.072175 * lb));
      if (dark) { const g = Math.exp(t); dst[p] = encode(lr * g); dst[p + 1] = encode(lg * g); dst[p + 2] = encode(lb * g); }
      else { dst[p] = encode(lr + (1 - lr) * t); dst[p + 1] = encode(lg + (1 - lg) * t); dst[p + 2] = encode(lb + (1 - lb) * t); }
      dst[p + 3] = 255;
    }
  }
  return dst;
}

// 스택: ① LUT → ③ C → ④ k_b → ⑤ 비네팅 → ⑥ 그레인. P = { lut, C, K, V, grain }, use = { C, K, V, grain }
export function applyStack(rgba, w, h, P, use) {
  const n = w * h;
  let img = applyLut33(rgba, P.lut, new Uint8ClampedArray(n * 4));
  if (use.C && P.C) img = applyTone(img, w, h, P.C, new Uint8ClampedArray(n * 4));
  if (use.K && P.K) img = applyBandGain(img, w, h, P.K, new Uint8ClampedArray(n * 4));
  if (use.V && P.V) img = applyVignette(img, w, h, P.V, new Uint8ClampedArray(n * 4));
  if (use.grain && P.grain) img = applyGrain(img, w, h, { ...P.grain, seed: P.grain.seed ?? 1 }, new Uint8ClampedArray(n * 4));
  return img;
}
