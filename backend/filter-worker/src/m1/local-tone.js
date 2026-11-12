// 지역 톤 C(Y, B) 읽기: 색 코어와 마커를 뺀 견본 전체(바탕 부채꼴, 톤 계단, 국소성·평탄·주파수·디헤이즈 패널, 완충띠)를 STRIDE 간격으로 표본해서
// 표 C를 최소제곱으로 맞춘다. 입력 견본은 합성이라 정확히 알기 때문에 잔차를 픽셀 단위로 잴 수 있다.
//   Y = L*(LUT(입력)), B = LUT(입력)의 log 휘도를 σ = 짧은 변 × pct로 흐린 값, R = L*(읽는 견본) − Y. σ는 견본 안 RMS로 고른다.
//   색 코어는 LUT가 나온 곳이라 잔차가 구성상 0이어서, 넣으면 표를 코어 주변 밝기 쪽 0으로 끌어당긴다(그래서 뺀다).
// 연산자(표 + 이중선형 보간)는 앱에 새로 넣을 연산자와 같은 식이다: tone = { ny, yLo, yHi, nb, bLo, bHi, sigmaPct, T }, T[k * ny + j] = L* 변화량.
import { blurLogLum, rgbToLab } from './color.js';
import { CORE, H, MARKERS, W } from './layout.js';
import { cholSolve } from './numeric.js';

// 짧은 변 1/2/5/10%
export const C_SIGMAS = [0.01, 0.02, 0.05, 0.1];
const STRIDE_C = 4; // 맞춤 표본 간격(px). 저주파 톤이라 촘촘할 필요가 없다
// 표 격자. B는 log(선형 휘도 + 0.001)이라 검정 −6.9 ~ 흰색 0을 덮는다
export const C_GRID = { ny: 17, yLo: 0, yHi: 100, nb: 15, bLo: -7, bHi: 0 };
// 이웃 매듭 차의 벌점 가중(표본 수 단위): 표본이 이보다 적은 매듭은 이웃 값으로 메워진다. 1차 차분이라 표본이 없는 바깥은 평평하게 잇는다
const LS_LAMBDA = 200;

// 입력 L*(앞 단계 출력)과 주변 log 휘도 B에서의 L* 변화량 (이중선형, 매듭 밖은 끝 값 유지)
export function toneDeltaAt(tone, L, B) {
  const { ny, nb, T } = tone;
  const ty = Math.min(ny - 1, Math.max(0, ((L - tone.yLo) / (tone.yHi - tone.yLo)) * (ny - 1))), j0 = Math.min(ny - 2, Math.floor(ty)), fy = ty - j0;
  if (nb === 1) return T[j0] * (1 - fy) + T[j0 + 1] * fy;
  const tb = Math.min(nb - 1, Math.max(0, ((B - tone.bLo) / (tone.bHi - tone.bLo)) * (nb - 1))), k0 = Math.min(nb - 2, Math.floor(tb)), fb = tb - k0;
  const a = k0 * ny + j0, b = a + ny;
  return (T[a] * (1 - fy) + T[a + 1] * fy) * (1 - fb) + (T[b] * (1 - fy) + T[b + 1] * fy) * fb;
}

// 표본 (Y, B, R)에 toneDeltaAt과 같은 (이중)선형 보간 표를 최소제곱으로 맞춘다(+ 이웃 매듭 차 벌점).
export function fitToneLS(Y, B, R, grid, sigmaPct = null, lambda = LS_LAMBDA) {
  const { ny, nb } = grid, m = ny * nb, A = new Float64Array(m * m), rhs = new Float64Array(m), idx = [0, 0, 0, 0], wt = [0, 0, 0, 0];
  for (let s = 0; s < R.length; s++) {
    const ty = Math.min(ny - 1, Math.max(0, ((Y[s] - grid.yLo) / (grid.yHi - grid.yLo)) * (ny - 1))), j0 = Math.min(ny - 2, Math.floor(ty)), fy = ty - j0;
    let c = 2;
    idx[0] = j0; wt[0] = 1 - fy; idx[1] = j0 + 1; wt[1] = fy;
    if (nb > 1) {
      const tb = Math.min(nb - 1, Math.max(0, ((B[s] - grid.bLo) / (grid.bHi - grid.bLo)) * (nb - 1))), k0 = Math.min(nb - 2, Math.floor(tb)), fb = tb - k0;
      idx[0] = k0 * ny + j0; idx[1] = k0 * ny + j0 + 1; idx[2] = idx[0] + ny; idx[3] = idx[1] + ny;
      wt[0] = (1 - fy) * (1 - fb); wt[1] = fy * (1 - fb); wt[2] = (1 - fy) * fb; wt[3] = fy * fb; c = 4;
    }
    for (let p = 0; p < c; p++) { rhs[idx[p]] += wt[p] * R[s]; for (let q = 0; q < c; q++) A[idx[p] * m + idx[q]] += wt[p] * wt[q]; }
  }
  const tie = (p, q) => { A[p * m + p] += lambda; A[q * m + q] += lambda; A[p * m + q] -= lambda; A[q * m + p] -= lambda; };
  for (let k = 0; k < nb; k++) for (let j = 0; j < ny; j++) { if (j + 1 < ny) tie(k * ny + j, k * ny + j + 1); if (k + 1 < nb) tie(k * ny + j, (k + 1) * ny + j); }
  for (let p = 0; p < m; p++) A[p * m + p] += 1e-9;
  return { ...grid, sigmaPct, T: cholSolve(A, rhs, m) };
}

const inside = (r, x, y) => x >= r.x && x < r.x + r.w && y >= r.y && y < r.y + r.h;
const labL = (rgba, p, t) => { rgbToLab(rgba[p], rgba[p + 1], rgba[p + 2], t, 0); return t[0]; };

// 표본: 코어·마커 밖 STRIDE_C 간격 픽셀. rendered = 읽는 견본(비네팅을 되돌린 것), pre = LUT(입력 견본).
// 큰 평면(L* 전체, 흐림 전체)을 남기지 않고 표본 위치의 값만 담는다. Float32로 담는 지점은 평면을 쓰던 연구 코드와 같다
export function cSamples(rendered, pre) {
  const skip = [CORE, ...MARKERS], idxList = new Int32Array(Math.ceil(H / STRIDE_C) * Math.ceil(W / STRIDE_C));
  let count = 0;
  for (let y = 0; y < H; y += STRIDE_C) for (let x = 0; x < W; x += STRIDE_C) if (!skip.some((r) => inside(r, x, y))) idxList[count++] = y * W + x;
  const idx = idxList.slice(0, count), Y = new Float32Array(count), R = new Float32Array(count), t = new Float64Array(3);
  for (let s = 0; s < count; s++) {
    const p = idx[s] * 4, lp = Math.fround(labL(pre, p, t)), lr = Math.fround(labL(rendered, p, t));
    Y[s] = lp; R[s] = lr - lp;
  }
  const Bs = C_SIGMAS.map((pct) => { const Bf = blurLogLum(pre, W, H, pct); return Float32Array.from(idx, (i) => Bf[i]); });
  return { idx, Y, R, Bs };
}

const rmsOf = (R, f) => { let se = 0; for (let i = 0; i < R.length; i++) se += (R[i] - f(i)) ** 2; return Math.sqrt(se / R.length); };

// σ 후보마다 표를 맞춘다. 반환: [{ pct, tone, rms }]
export function fitC(smp) {
  return C_SIGMAS.map((pct, k) => {
    const tone = fitToneLS(smp.Y, smp.Bs[k], smp.R, C_GRID, pct);
    return { pct, tone, rms: rmsOf(smp.R, (i) => toneDeltaAt(tone, smp.Y[i], smp.Bs[k][i])) };
  });
}

export const bestOf = (Cs) => Cs.reduce((b, c, k) => (c.rms < Cs[b].rms ? k : b), 0);
