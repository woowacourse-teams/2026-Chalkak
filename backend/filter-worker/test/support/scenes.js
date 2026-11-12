// 시험용 합성 장면과 색 차이. 읽은 레시피를 사진에 다시 걸어 정답과 비교할 때 쓴다(3:4 세로 장면 3종).
import { deltaE2000, rgbToLab } from '../../src/m1/color.js';

export const mulberry = (a) => () => {
  a = (a + 0x6d2b79f5) | 0;
  let t = Math.imul(a ^ (a >>> 15), 1 | a);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
const clamp255 = (v) => Math.min(255, Math.max(0, Math.round(v)));

function latticeNoise(w, h, cell, seed) {
  const rand = mulberry(seed);
  const gw = Math.ceil(w / cell) + 2, gh = Math.ceil(h / cell) + 2;
  const g = Float32Array.from({ length: gw * gh }, () => rand() * 2 - 1);
  const out = new Float32Array(w * h);
  const sm = (t) => t * t * (3 - 2 * t);
  for (let y = 0; y < h; y++) {
    const fy = y / cell, y0 = Math.floor(fy), ty = sm(fy - y0);
    for (let x = 0; x < w; x++) {
      const fx = x / cell, x0 = Math.floor(fx), tx = sm(fx - x0);
      const a = g[y0 * gw + x0], b = g[y0 * gw + x0 + 1], c = g[(y0 + 1) * gw + x0], d = g[(y0 + 1) * gw + x0 + 1];
      out[y * w + x] = (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
    }
  }
  return out;
}

// 하늘·풀·흙길·돌벽·어두운 창·밝은 창·주황 공이 있는 장면. k = 0, 1, 2는 지평선과 좌우가 다르다
export function makeScene(k, SW = 384, SH = 512) {
  const n1 = latticeNoise(SW, SH, 3, 900 + k), n2 = latticeNoise(SW, SH, 9, 950 + k);
  const horizon = [0.68, 0.5, 0.26][k], ea = [0.35, 0.5, 0.3][k], eb = [0.65, 0.78, 0.6][k];
  const out = new Uint8ClampedArray(SW * SH * 4);
  for (let y = 0; y < SH; y++) {
    for (let x = 0; x < SW; x++) {
      const i = y * SW + x;
      let u = x / (SW - 1);
      const v = y / (SH - 1);
      if (k === 2) u = 1 - u;
      const tex = 0.6 * n1[i] + 0.4 * n2[i];
      let c;
      if (v < horizon) { const t = v / horizon; c = [110 + 60 * t, 150 + 45 * t, 215 + 15 * t]; }
      else if (u < ea) c = [60 + tex * 14, 110 + tex * 26, 40 + tex * 10];
      else if (u < eb) { const t = (v - horizon) / (1 - horizon); c = [215 - 40 * t, 125 - 30 * t, 55 - 15 * t]; }
      else c = [130 + tex * 24, 125 + tex * 24, 118 + tex * 24];
      if (u > 0.05 && u < 0.2 && v > 0.08 && v < 0.3) c = [28 + 20 * v, 26 + 20 * v, 38 + 20 * v];
      if (u > 0.8 && u < 0.95 && v > 0.05 && v < 0.25) c = [244 - 20 * v, 240 - 20 * v, 226 - 20 * v];
      if (Math.hypot(u - 0.5, (v - 0.75) * 0.75) < 0.12) c = [205 + tex * 20, 112 + tex * 20, 48 + tex * 12];
      const r = Math.hypot(x - (SW - 1) / 2, y - (SH - 1) / 2) / Math.hypot(SW / 2, SH / 2);
      const f = 1.05 - 0.1 * r * r;
      for (let ch = 0; ch < 3; ch++) out[i * 4 + ch] = clamp255(c[ch] * f);
      out[i * 4 + 3] = 255;
    }
  }
  return out;
}

// 두 RGBA의 픽셀별 ΔE2000 평균
export function meanDeltaE(a, b, pixelCount) {
  const la = new Float64Array(3), lb = new Float64Array(3);
  let sum = 0;
  for (let i = 0; i < pixelCount; i++) {
    const p = i * 4;
    rgbToLab(a[p], a[p + 1], a[p + 2], la, 0);
    rgbToLab(b[p], b[p + 1], b[p + 2], lb, 0);
    sum += deltaE2000(la[0], la[1], la[2], lb[0], lb[1], lb[2]);
  }
  return sum / pixelCount;
}
