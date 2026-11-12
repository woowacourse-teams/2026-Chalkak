// 색 변환과 이미지 평면 도구. 연구 코드(core.js·e1-ops.js)와 같은 식을 쓴다. 12.6M px 이미지를 1GB 안에서 다루려고
// 흐림은 평면 복사본을 하나만 쓰는 제자리 방식으로 다시 짰다(상자 필터의 합산 순서와 Float32 반올림 지점은 연구 코드와 같다)

export const LIN = Float64Array.from({ length: 256 }, (_, i) => {
  const c = i / 255;
  return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
});
const EPS = 216 / 24389;
const KAPPA = 24389 / 27;
const M = [[0.4124564, 0.3575761, 0.1804375], [0.2126729, 0.7151522, 0.072175], [0.0193339, 0.119192, 0.9503041]];
const MINV = (() => {
  const [[a, b, c], [d, e, f], [g, h, i]] = M;
  const det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
  return [
    [(e * i - f * h) / det, (c * h - b * i) / det, (b * f - c * e) / det],
    [(f * g - d * i) / det, (a * i - c * g) / det, (c * d - a * f) / det],
    [(d * h - e * g) / det, (b * g - a * h) / det, (a * e - b * d) / det],
  ];
})();

// 선형 값 → sRGB 8비트
export const encode = (v) => {
  const x = v <= 0.0031308 ? 12.92 * v : 1.055 * Math.pow(Math.max(0, v), 1 / 2.4) - 0.055;
  return Math.min(255, Math.max(0, Math.round(x * 255)));
};
export const lumLin = (r, g, b) => 0.2126729 * LIN[r] + 0.7151522 * LIN[g] + 0.072175 * LIN[b];
export const lstar = (Y) => (Y > EPS ? 116 * Math.cbrt(Y) - 16 : KAPPA * Y);

const labF = (t) => (t > EPS ? Math.cbrt(t) : (KAPPA * t + 16) / 116);
// sRGB(D65) → CIELAB. out[o..o+2]에 쓴다
export function rgbToLab(r, g, b, out, o) {
  const R = LIN[r], G = LIN[g], B = LIN[b];
  const X = (0.4124564 * R + 0.3575761 * G + 0.1804375 * B) / 0.95047;
  const Y = 0.2126729 * R + 0.7151522 * G + 0.072175 * B;
  const Z = (0.0193339 * R + 0.119192 * G + 0.9503041 * B) / 1.08883;
  const fx = labF(X), fy = labF(Y), fz = labF(Z);
  out[o] = 116 * fy - 16;
  out[o + 1] = 500 * (fx - fy);
  out[o + 2] = 200 * (fy - fz);
}

const DEG = Math.PI / 180;
const POW25_7 = 6103515625;
export function deltaE2000(L1, a1, b1, L2, a2, b2) {
  const C1 = Math.hypot(a1, b1);
  const C2 = Math.hypot(a2, b2);
  const Cb7 = Math.pow((C1 + C2) / 2, 7);
  const G = 0.5 * (1 - Math.sqrt(Cb7 / (Cb7 + POW25_7)));
  const a1p = (1 + G) * a1;
  const a2p = (1 + G) * a2;
  const C1p = Math.hypot(a1p, b1);
  const C2p = Math.hypot(a2p, b2);
  let h1p = b1 === 0 && a1p === 0 ? 0 : Math.atan2(b1, a1p);
  if (h1p < 0) h1p += 2 * Math.PI;
  let h2p = b2 === 0 && a2p === 0 ? 0 : Math.atan2(b2, a2p);
  if (h2p < 0) h2p += 2 * Math.PI;

  const dLp = L2 - L1;
  const dCp = C2p - C1p;
  let dhp = 0;
  if (C1p * C2p !== 0) {
    dhp = h2p - h1p;
    if (dhp > Math.PI) dhp -= 2 * Math.PI;
    else if (dhp < -Math.PI) dhp += 2 * Math.PI;
  }
  const dHp = 2 * Math.sqrt(C1p * C2p) * Math.sin(dhp / 2);

  const Lbp = (L1 + L2) / 2;
  const Cbp = (C1p + C2p) / 2;
  let hbp;
  if (C1p * C2p === 0) hbp = h1p + h2p;
  else if (Math.abs(h1p - h2p) <= Math.PI) hbp = (h1p + h2p) / 2;
  else if (h1p + h2p < 2 * Math.PI) hbp = (h1p + h2p + 2 * Math.PI) / 2;
  else hbp = (h1p + h2p - 2 * Math.PI) / 2;

  const T = 1 - 0.17 * Math.cos(hbp - 30 * DEG) + 0.24 * Math.cos(2 * hbp) + 0.32 * Math.cos(3 * hbp + 6 * DEG) - 0.2 * Math.cos(4 * hbp - 63 * DEG);
  const dTheta = 30 * DEG * Math.exp(-Math.pow((hbp / DEG - 275) / 25, 2));
  const Cbp7 = Math.pow(Cbp, 7);
  const Rc = 2 * Math.sqrt(Cbp7 / (Cbp7 + POW25_7));
  const Lm = (Lbp - 50) * (Lbp - 50);
  const Sl = 1 + (0.015 * Lm) / Math.sqrt(20 + Lm);
  const Sc = 1 + 0.045 * Cbp;
  const Sh = 1 + 0.015 * Cbp * T;
  const Rt = -Math.sin(2 * dTheta) * Rc;
  const l = dLp / Sl, c = dCp / Sc, h = dHp / Sh;
  return Math.sqrt(l * l + c * c + h * h + Rt * c * h);
}

// ---- 평면 ----
export function lstarPlane(rgba, count, out = new Float32Array(count)) {
  const t = new Float64Array(3);
  for (let i = 0; i < count; i++) {
    rgbToLab(rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], t, 0);
    out[i] = t[0];
  }
  return out;
}

export function labPlanes(rgba, count) {
  const L = new Float32Array(count), a = new Float32Array(count), b = new Float32Array(count), t = new Float64Array(3);
  for (let i = 0; i < count; i++) {
    rgbToLab(rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], t, 0);
    L[i] = t[0]; a[i] = t[1]; b[i] = t[2];
  }
  return { L, a, b };
}

// L 평면에 dL을 더한 값(0~100으로 자름)과 a·b로 sRGB를 만든다. dL이 없으면 왕복만 한다
export function labToRgba(lab, count, dL, dst) {
  const f = (t) => { const t3 = t * t * t; return t3 > EPS ? t3 : (116 * t - 16) / KAPPA; };
  for (let i = 0; i < count; i++) {
    const L = Math.min(100, Math.max(0, lab.L[i] + (dL ? dL[i] : 0)));
    const fy = (L + 16) / 116, fx = fy + lab.a[i] / 500, fz = fy - lab.b[i] / 200;
    const X = f(fx) * 0.95047, Y = (L > 8 ? fy * fy * fy : L / KAPPA), Z = f(fz) * 1.08883;
    for (let ch = 0; ch < 3; ch++) dst[i * 4 + ch] = encode(MINV[ch][0] * X + MINV[ch][1] * Y + MINV[ch][2] * Z);
    dst[i * 4 + 3] = 255;
  }
  return dst;
}

// log(선형 휘도 + 0.001)
function logLuminance(rgba, count, out = new Float32Array(count)) {
  for (let i = 0; i < count; i++) out[i] = Math.log(lumLin(rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2]) + 0.001);
  return out;
}

// ---- 흐림 ----
// 상자 필터 한 번(가장자리는 끝 값을 늘려 채움)을 plane 안에서 제자리로 한다. 줄 단위로 원본을 line에 복사해 두고 거기서 읽는다
function boxLine(line, len, r, win, write) {
  let acc = (r + 1) * line[0];
  for (let k = 1; k <= r; k++) acc += line[Math.min(k, len - 1)];
  for (let x = 0; x < len; x++) {
    write(x, acc / win);
    acc += line[Math.min(x + r + 1, len - 1)] - line[Math.max(x - r, 0)];
  }
}

const TILE = 32;
function boxPass(plane, w, h, r) {
  if (r <= 0) return;
  const win = 2 * r + 1;
  const row = new Float32Array(w);
  for (let y = 0; y < h; y++) {
    const o = y * w;
    row.set(plane.subarray(o, o + w));
    boxLine(row, w, r, win, (x, v) => { plane[o + x] = v; });
  }
  // 세로는 열 32개씩 묶어 가로 방향으로 읽고 쓴다(캐시 적중)
  const cols = new Float32Array(TILE * h);
  for (let x0 = 0; x0 < w; x0 += TILE) {
    const tw = Math.min(TILE, w - x0);
    for (let y = 0; y < h; y++) for (let t = 0; t < tw; t++) cols[t * h + y] = plane[y * w + x0 + t];
    for (let t = 0; t < tw; t++) {
      const line = cols.subarray(t * h, (t + 1) * h), x = x0 + t;
      boxLine(line, h, r, win, (y, v) => { plane[y * w + x] = v; });
    }
  }
}

// 상자 3번으로 가우시안을 근사한다(상자 크기는 σ에서 정함). σ는 픽셀 단위. 입력 평면을 직접 흐린다
export function gaussianBlurInPlace(plane, w, h, sigma) {
  if (!(sigma >= 0.3)) return plane;
  const passes = 3;
  const ideal = Math.sqrt((12 * sigma * sigma) / passes + 1);
  let wl = Math.floor(ideal);
  if (wl % 2 === 0) wl--;
  const wu = wl + 2;
  const m = Math.round((12 * sigma * sigma - passes * wl * wl - 4 * passes * wl - 3 * passes) / (-4 * wl - 4));
  for (let i = 0; i < passes; i++) boxPass(plane, w, h, ((i < m ? wl : wu) - 1) / 2);
  return plane;
}

// 입력은 그대로 두고 흐린 복사본을 돌려준다
export function gaussianBlur(src, w, h, sigma) {
  const out = new Float32Array(src.length);
  out.set(src);
  return gaussianBlurInPlace(out, w, h, sigma);
}

// 흐린 결과를 미리 만들어 둔 평면 out에 쓴다(같은 크기의 평면을 되풀이해서 쓰는 곳에서 할당을 없앤다)
export function gaussianBlurInto(out, src, w, h, sigma) {
  out.set(src);
  return gaussianBlurInPlace(out, w, h, sigma);
}

// 주변 log-휘도: log 휘도를 가우시안 σ = 짧은 변 × pct로 흐린 값
// out을 주면 그 평면에 쓴다
export function blurLogLum(rgba, w, h, pct, out = undefined) {
  return gaussianBlurInPlace(logLuminance(rgba, w * h, out), w, h, Math.max(1, pct * Math.min(w, h)));
}

// ---- 33³ LUT ----
const LUT_SIZE = 33;
// sRGB 감마 공간 trilinear 조회. dst는 src와 같은 길이의 RGBA
export function applyLut33(src, lut, dst) {
  const n = LUT_SIZE, scale = (n - 1) / 255;
  for (let p = 0; p < src.length; p += 4) {
    const pr = src[p] * scale, pg = src[p + 1] * scale, pb = src[p + 2] * scale;
    const r0 = Math.floor(pr), g0 = Math.floor(pg), b0 = Math.floor(pb);
    const fr = pr - r0, fg = pg - g0, fb = pb - b0;
    const r1 = Math.min(r0 + 1, n - 1), g1 = Math.min(g0 + 1, n - 1) * n, b1 = Math.min(b0 + 1, n - 1) * n * n;
    const g0n = g0 * n, b0n = b0 * n * n;
    const c000 = (r0 + g0n + b0n) * 3, c100 = (r1 + g0n + b0n) * 3;
    const c010 = (r0 + g1 + b0n) * 3, c110 = (r1 + g1 + b0n) * 3;
    const c001 = (r0 + g0n + b1) * 3, c101 = (r1 + g0n + b1) * 3;
    const c011 = (r0 + g1 + b1) * 3, c111 = (r1 + g1 + b1) * 3;
    for (let ch = 0; ch < 3; ch++) {
      const x00 = lut[c000 + ch] + (lut[c100 + ch] - lut[c000 + ch]) * fr;
      const x10 = lut[c010 + ch] + (lut[c110 + ch] - lut[c010 + ch]) * fr;
      const x01 = lut[c001 + ch] + (lut[c101 + ch] - lut[c001 + ch]) * fr;
      const x11 = lut[c011 + ch] + (lut[c111 + ch] - lut[c011 + ch]) * fr;
      const y0 = x00 + (x10 - x00) * fg;
      const y1 = x01 + (x11 - x01) * fg;
      dst[p + ch] = Math.round(Math.min(255, Math.max(0, y0 + (y1 - y0) * fb)));
    }
    dst[p + 3] = 255;
  }
  return dst;
}

// 같은 격자 LUT 두 개의 항목별 ΔE2000 평균·최대 (Uint8 RGB 배열)
export function lutDeltaE(a, b) {
  const la = new Float64Array(3), lb = new Float64Array(3), n = a.length / 3;
  let s = 0, mx = 0;
  for (let i = 0; i < n; i++) {
    rgbToLab(a[i * 3], a[i * 3 + 1], a[i * 3 + 2], la, 0);
    rgbToLab(b[i * 3], b[i * 3 + 1], b[i * 3 + 2], lb, 0);
    const d = deltaE2000(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
    s += d;
    if (d > mx) mx = d;
  }
  return { mean: s / n, max: mx };
}
