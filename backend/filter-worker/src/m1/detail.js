// 디테일 k_b 읽기: 주파수 패널과 5° 에지에서 대역 이득 표를 최소제곱으로 맞춘다.
//   연산자 입력 L_in = L*(LUT(입력 견본)) [C가 켜져 있으면 L_in + C], 잔차 R = L*(읽는 견본) − L_in.
//   대역·포락·평균은 앱과 같은 방식으로 L_in에서 구하므로 상자 흐림 근사까지 앱과 같다. R ≈ Σ_b D_b · k_b(E_b, M)은 표 값에 대해 선형이라 바로 푼다(+ 이웃 매듭 차 벌점).
//
// 연산자 정의 (L* 평면 하나에만 건다. a·b는 그대로 둔다):
//   흐림 B_s = gaussianBlur(L, s · scale), 대역 D_b = B_{s_b} − B_{s_{b+1}} (픽셀 고정 DoG, s = sigmas. 0이면 흐리지 않은 L)
//   포락 E_b = √(2 · gaussianBlur(D_b², 2 · s_{b+1} · scale)) — 사인파면 그 대역에 실린 진폭과 같다 (L* 단위).
//   지역 평균 M = B_{s_last} (가장 넓은 흐림, 기본 32 px)
//   L_out = L + Σ_b D_b · k_b(E_b, M), k_b는 (log E) × M 이중선형 표 (매듭 밖은 끝 값 유지)
// scale: 표를 잰 해상도 대비 적용 해상도 비율(견본과 원본 크기에서 1).
import { gaussianBlur, gaussianBlurInPlace, gaussianBlurInto, lstarPlane } from './color.js';
import { H, LAYOUT, W } from './layout.js';
import { cholSolve } from './numeric.js';

const BAND_SIGMAS_0 = [0, 1, 2, 4, 8, 16, 32]; // 0-1, 1-2, 2-4, 4-8, 8-16, 16-32 px
export const BAND_E_KNOTS = [0.15, 0.6, 2.4, 9.6]; // 대역 포락(L*). 4배 간격
export const BAND_M_KNOTS = [30, 55, 80]; // 주파수 패널의 평균 L*
// 이 연산자의 표 틀: 진폭(포락) × 평균 밝기 표에 0-1 px 대역을 더한 것
export const KIND_AMP0 = { sigmas: BAND_SIGMAS_0, eKnots: BAND_E_KNOTS, mKnots: BAND_M_KNOTS };

// 표 틀: K 길이 = 대역 수 × eKnots × mKnots, 순서 ((b · ne) + j) · nm + l
export function makeBandGain({ sigmas, eKnots, mKnots }) {
  const nb = sigmas.length - 1;
  return { sigmas: [...sigmas], eKnots: [...eKnots], mKnots: [...mKnots], K: new Float64Array(nb * eKnots.length * mKnots.length) };
}

// 매듭 축 하나의 (왼쪽 매듭, 오른쪽 가중). xs는 이미 log를 취한(또는 그대로인) 매듭, 매듭 밖은 끝 값
function axis(xs, x) {
  const n = xs.length;
  if (n === 1 || x <= xs[0]) return [0, 0];
  if (x >= xs[n - 1]) return [n - 2, 1];
  let j = 0;
  while (j < n - 2 && x >= xs[j + 1]) j++;
  return [j, (x - xs[j]) / (xs[j + 1] - xs[j])];
}
const logKnots = new WeakMap(); // eKnots 배열 → log 매듭 (픽셀마다 log를 다시 구하지 않으려고)
const logOf = (ks) => { let v = logKnots.get(ks); if (!v) logKnots.set(ks, (v = ks.map(Math.log))); return v; };

// 한 대역 안에서 (E, M)이 걸치는 매듭(최대 4개)의 대역 내 위치와 가중을 idx·wt에 쓰고 개수를 돌려준다. 포락 축은 log E
export function bandKnotWeights(kb, E, M, idx, wt) {
  const ne = kb.eKnots.length, nm = kb.mKnots.length, [j, fe] = axis(logOf(kb.eKnots), Math.log(Math.max(E, 1e-9))), [l, fm] = axis(kb.mKnots, M);
  let c = 0;
  for (let a = 0; a < (ne > 1 ? 2 : 1); a++) for (let b = 0; b < (nm > 1 ? 2 : 1); b++) {
    const w = (ne > 1 ? (a ? fe : 1 - fe) : 1) * (nm > 1 ? (b ? fm : 1 - fm) : 1);
    if (w === 0) continue;
    idx[c] = (j + a) * nm + l + b; wt[c] = w; c++;
  }
  return c;
}

// 대역·포락·지역 평균 평면 (작은 영역용: 대역마다 평면을 모두 들고 있는다)
export function bandFeatures(L, w, h, kb, scale = 1) {
  const sig = kb.sigmas.map((s) => s * scale), nb = sig.length - 1, bands = [], env = kb.eKnots.length > 1 ? [] : null;
  let prev = sig[0] === 0 ? L : gaussianBlur(L, w, h, sig[0]), next = null;
  for (let b = 0; b < nb; b++) {
    next = gaussianBlur(L, w, h, sig[b + 1]);
    const D = new Float32Array(L.length);
    for (let i = 0; i < D.length; i++) D[i] = prev[i] - next[i];
    bands.push(D);
    if (env) {
      const e = new Float32Array(D.length);
      for (let i = 0; i < e.length; i++) e[i] = 2 * D[i] * D[i];
      gaussianBlurInPlace(e, w, h, 2 * sig[b + 1]);
      for (let i = 0; i < e.length; i++) e[i] = Math.sqrt(Math.max(0, e[i]));
      env.push(e);
    }
    prev = next;
  }
  return { bands, env, mean: kb.mKnots.length > 1 ? next : null };
}

// L*에 더할 값 Σ_b D_b · k_b(E_b, M). 대역을 하나씩 계산하고 평면은 3장만 되풀이해 쓴다(전체 견본 크기에서 메모리를 아끼려고. 결과는 bandFeatures로 계산한 것과 같다).
// only(Int32Array)를 주면 그 위치의 값만 돌려준다(결과 길이 = only.length)
export function bandGainDelta(L, w, h, kb, scale = 1, only = null) {
  const n = only ? only.length : w * h, out = new Float32Array(n);
  if (kb.K.every((v) => v === 0)) return out;
  const sig = kb.sigmas.map((s) => s * scale), nb = sig.length - 1, per = kb.eKnots.length * kb.mKnots.length, idx = [0, 0, 0, 0], wt = [0, 0, 0, 0];
  const sample = (plane) => { if (!only) return plane.slice(); const s = new Float32Array(n); for (let q = 0; q < n; q++) s[q] = plane[only[q]]; return s; };
  const X = new Float32Array(L.length), Y = new Float32Array(L.length), work = new Float32Array(L.length);
  const mean = kb.mKnots.length > 1 ? sample(gaussianBlurInto(work, L, w, h, sig[nb])) : null;
  let prev = L;
  if (sig[0] !== 0) { gaussianBlurInto(X, L, w, h, sig[0]); prev = X; }
  for (let b = 0; b < nb; b++) {
    const next = prev === X ? Y : X;
    gaussianBlurInto(next, L, w, h, sig[b + 1]);
    for (let i = 0; i < work.length; i++) work[i] = prev[i] - next[i];
    const D = sample(work); // 포락을 work에서 제자리로 만들기 전에 대역 값을 빼 둔다
    if (kb.eKnots.length > 1) {
      for (let i = 0; i < work.length; i++) work[i] = 2 * work[i] * work[i];
      gaussianBlurInPlace(work, w, h, 2 * sig[b + 1]);
      for (let i = 0; i < work.length; i++) work[i] = Math.sqrt(Math.max(0, work[i]));
    }
    const off = b * per;
    for (let q = 0; q < n; q++) {
      const c = bandKnotWeights(kb, kb.eKnots.length > 1 ? work[only ? only[q] : q] : 1, mean ? mean[q] : 0, idx, wt);
      let g = 0;
      for (let k = 0; k < c; k++) g += wt[k] * kb.K[off + idx[k]];
      out[q] += D[q] * g;
    }
    prev = next;
  }
  return out;
}

// ---------------------------------------------------------------- 표 맞춤
// 맞춤 영역: 주파수 패널과 에지. 대역·포락을 앱과 똑같이(영역 경계 효과 없이) 구하려고 MARGIN px 더 잘라 계산하고, 표본은 사각형 안에서만 쓴다.
// 가장 넓은 흐림 σ 32의 상자 3번 근사 지지 반경(≈ 3σ = 96)에 포락 흐림(σ 64, ≈ 192)이 더 붙으므로 그 합(288)보다 넉넉히 둔다
const MARGIN = 320;
const FIT_REGIONS = LAYOUT.regions.filter((r) => r.kind === 'freqPanel' || r.kind === 'edge').map((r) => ({ name: r.name, x: r.x, y: r.y, w: r.w, h: r.h }));
const STRIDE_Y = 2; // 사인은 가로로만 바뀌어 세로로 한 줄 걸러 써도 정보가 같다
// 이웃 매듭 차 벌점 = LAMBDA_REL × (표본이 있는 매듭 가중의 중앙값). 0.01이면 데이터가 있는 매듭에서 맞춤을 1% 정도만 끌어당긴다
const LAMBDA_REL = 0.01;

// 영역마다 (여백 포함) 잘라 낸 L* 평면
function cropL(rgba, r) {
  const x0 = Math.max(0, r.x - MARGIN), y0 = Math.max(0, r.y - MARGIN), x1 = Math.min(W, r.x + r.w + MARGIN), y1 = Math.min(H, r.y + r.h + MARGIN);
  const w = x1 - x0, h = y1 - y0, sub = new Uint8ClampedArray(w * h * 4);
  for (let y = 0; y < h; y++) sub.set(rgba.subarray(((y0 + y) * W + x0) * 4, ((y0 + y) * W + x1) * 4), y * w * 4);
  return { L: lstarPlane(sub, w * h), w, h, ix: r.x - x0, iy: r.y - y0, iw: r.w, ih: r.h, x0, y0 };
}
export const regionPlanes = (rgba) => FIT_REGIONS.map((r) => cropL(rgba, r));

// 대역 이득 표 맞춤. ins/outs = regionPlanes 결과(연산자 입력 / 읽는 견본)
export function fitBandGain(ins, outs, tmpl) {
  const kb = makeBandGain(tmpl), nb = kb.sigmas.length - 1, per = kb.eKnots.length * kb.mKnots.length, m = nb * per;
  const A = new Float64Array(m * m), rhs = new Float64Array(m), idx = [0, 0, 0, 0], wt = [0, 0, 0, 0];
  const cols = new Int32Array(4 * nb), vals = new Float64Array(4 * nb);
  ins.forEach((p, r) => {
    const f = bandFeatures(p.L, p.w, p.h, kb), Lo = outs[r].L;
    for (let y = p.iy; y < p.iy + p.ih; y += STRIDE_Y) for (let x = p.ix; x < p.ix + p.iw; x++) {
      const i = y * p.w + x, R = Lo[i] - p.L[i];
      let c = 0;
      for (let b = 0; b < nb; b++) {
        const k = bandKnotWeights(kb, f.env ? f.env[b][i] : 1, f.mean ? f.mean[i] : 0, idx, wt), D = f.bands[b][i];
        for (let q = 0; q < k; q++) { cols[c] = b * per + idx[q]; vals[c] = D * wt[q]; c++; }
      }
      for (let a = 0; a < c; a++) { rhs[cols[a]] += vals[a] * R; for (let q = 0; q < c; q++) A[cols[a] * m + cols[q]] += vals[a] * vals[q]; }
    }
  });
  const diag = Array.from({ length: m }, (_, p) => A[p * m + p]).filter((v) => v > 0).sort((a, b) => a - b);
  const lambda = LAMBDA_REL * (diag.length ? diag[diag.length >> 1] : 1);
  const tie = (p, q) => { A[p * m + p] += lambda; A[q * m + q] += lambda; A[p * m + q] -= lambda; A[q * m + p] -= lambda; };
  const ne = kb.eKnots.length, nm = kb.mKnots.length;
  for (let b = 0; b < nb; b++) for (let j = 0; j < ne; j++) for (let l = 0; l < nm; l++) {
    const p = b * per + j * nm + l;
    if (j + 1 < ne) tie(p, p + nm);
    if (l + 1 < nm) tie(p, p + 1);
  }
  for (let p = 0; p < m; p++) A[p * m + p] += 1e-9 + lambda * 1e-6;
  kb.K = cholSolve(A, rhs, m);
  return kb;
}
