// 격자 보정의 공통 기계: 위치·밝기·주변 밝기 축 위의 다중 선형 꼭짓점마다 3×4 아핀(입력 RGB, 1 → 출력 RGB)을 두고,
// 견본 표본에서 최소제곱(+ 이웃 꼭짓점 매끄러움 + 항등 능선)으로 맞춘다. 식·순서·문턱은 연구 코드(e9a-grid, e15-gridplus)와 같다(결과 숫자가 같아야 한다).
import { ExtractionFailure } from '../failure.js';
import { clamp } from '../m1/numeric.js';

// A2: LUT 출력 → 격자. 축 r(반경) × g(감마 휘도) × B(무편집 입력의 흐린 log 휘도 1%)
export const A2_SPEC = { axes: ['r', 'g', 'B'], n: [5, 9, 7], pct: 0.01, ls: 1e-4 };
// G1(변형 G1c): M1 출력 → 잔차 격자. 격자가 할 일이 없으면 "M1 그대로"로 끌어 준다
export const G1_SPEC = { axes: ['r', 'g', 'B'], n: [3, 5, 5], pct: 0.01, ls: 1e-3 };

export const RIDGE = 1e-6; // 항등으로 끄는 능선 가중
export const B_LO = -6, B_HI = 0; // 흐린 log 휘도를 0~1로 펴는 범위
export const STRIDE = 4, OFFSET = 2; // 견본 표본 간격과 시작 위치
export const FOLD_BLOCKS = { sel: 1024, ref: 256 }; // 홀드아웃 무리를 가르는 체커 블록 크기(px). sel = 선택용, ref = 연구 A2 맞춤과 같은 256

export const nVertices = (spec) => spec.n.reduce((a, b) => a * b, 1);
export const nParams = (spec) => 4 * nVertices(spec);
export const gridDims = (spec) => [...spec.n, 4, 3];
// 분기별 격자 틀: 레시피가 담는 dims와 맞는지 검사하는 데 쓴다
export const GRID_KINDS = { A2: { dims: gridDims(A2_SPEC) }, G1: { dims: gridDims(G1_SPEC) } };

export const gammaLum = (r, g, b) => (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255;
export const radius = (x, y, w, h) => {
  const u = (x + 0.5 - w / 2) / (w / 2), v = (y + 0.5 - h / 2) / (h / 2);
  return clamp(Math.sqrt(u * u + v * v) / Math.SQRT2, 0, 1);
};
export const bNorm = (b) => clamp((b - B_LO) / (B_HI - B_LO), 0, 1);

// 격자 좌표(0~1 축 값) → 다중 선형 꼭짓점 번호·가중치. idx·wt 길이 2^D. 반환: 꼭짓점 수
export function corners(spec, q, idx, wt) {
  const D = spec.n.length;
  let k = 1, stride = 1;
  idx[0] = 0; wt[0] = 1;
  for (let d = 0; d < D; d++) {
    const t = q[d] * (spec.n[d] - 1), i0 = Math.min(spec.n[d] - 2, Math.floor(t)), f = t - i0;
    for (let j = 0; j < k; j++) { idx[j + k] = idx[j] + (i0 + 1) * stride; wt[j + k] = wt[j] * f; idx[j] += i0 * stride; wt[j] *= 1 - f; }
    k *= 2; stride *= spec.n[d];
  }
  return k;
}

// 표본 묶음 S: n, zin(격자 입력 0~255, n×3), tgt(정답 0~255, n×3), q(축 값, n×D), fold(0/1). 무리마다 정규 방정식을 따로 쌓는다
export function accumulate(spec, S) {
  const P = nParams(spec), D = spec.n.length, K = 1 << D;
  const H = [new Float64Array(P * P), new Float64Array(P * P)], R = [new Float64Array(P * 3), new Float64Array(P * 3)], cnt = [0, 0];
  const idx = new Int32Array(K), wt = new Float64Array(K), q = new Float64Array(D), pi = new Int32Array(4 * K), pv = new Float64Array(4 * K);
  for (let s = 0; s < S.n; s++) {
    for (let d = 0; d < D; d++) q[d] = S.q[s * D + d];
    corners(spec, q, idx, wt);
    const z = [S.zin[s * 3] / 255, S.zin[s * 3 + 1] / 255, S.zin[s * 3 + 2] / 255, 1];
    let m = 0;
    for (let a = 0; a < K; a++) for (let i = 0; i < 4; i++) { pi[m] = idx[a] * 4 + i; pv[m] = wt[a] * z[i]; m++; }
    const f = S.fold[s], h = H[f], r = R[f], t0 = S.tgt[s * 3] / 255, t1 = S.tgt[s * 3 + 1] / 255, t2 = S.tgt[s * 3 + 2] / 255;
    cnt[f]++;
    for (let a = 0; a < m; a++) {
      const va = pv[a];
      if (va === 0) continue;
      const row = pi[a] * P;
      for (let b = 0; b < m; b++) h[row + pi[b]] += va * pv[b];
      r[pi[a] * 3] += va * t0; r[pi[a] * 3 + 1] += va * t1; r[pi[a] * 3 + 2] += va * t2;
    }
  }
  return { H, R, cnt };
}

// parts: 쓸 무리 번호들. 데이터 항을 표본 수로 나누고 매끄러움·능선을 더해 촐레스키로 푼다.
// 반환: θ (Float64Array, 길이 P×3. 순서 [꼭짓점][입력 성분 R,G,B,1][출력 채널 3], 꼭짓점 번호는 첫 축이 가장 빨리 변함)
export function solve(spec, acc, parts) {
  const P = nParams(spec), A = new Float64Array(P * P), B = new Float64Array(P * 3);
  const N = parts.reduce((s, f) => s + acc.cnt[f], 0);
  for (const f of parts) {
    const h = acc.H[f], r = acc.R[f];
    for (let i = 0; i < P * P; i++) A[i] += h[i] / N;
    for (let i = 0; i < P * 3; i++) B[i] += r[i] / N;
  }
  const V = P / 4;
  let stride = 1;
  for (let d = 0; d < spec.n.length; d++) {
    for (let v = 0; v < V; v++) {
      if (Math.floor(v / stride) % spec.n[d] === spec.n[d] - 1) continue;
      const u = v + stride;
      for (let i = 0; i < 4; i++) { const a = v * 4 + i, b = u * 4 + i; A[a * P + a] += spec.ls; A[b * P + b] += spec.ls; A[a * P + b] -= spec.ls; A[b * P + a] -= spec.ls; }
    }
    stride *= spec.n[d];
  }
  for (let v = 0; v < V; v++) for (let i = 0; i < 4; i++) { const a = v * 4 + i; A[a * P + a] += RIDGE; if (i < 3) B[a * 3 + i] += RIDGE; }
  // 촐레스키(아래 삼각, 제자리)
  for (let j = 0; j < P; j++) {
    let s = A[j * P + j];
    for (let k = 0; k < j; k++) s -= A[j * P + k] * A[j * P + k];
    if (!(s > 0)) throw ExtractionFailure.processingFailed(`격자 정규 행렬이 양의 정부호가 아니에요 (j=${j}, s=${s})`);
    const L = Math.sqrt(s);
    A[j * P + j] = L;
    for (let i = j + 1; i < P; i++) {
      let t = A[i * P + j];
      const ri = i * P, rj = j * P;
      for (let k = 0; k < j; k++) t -= A[ri + k] * A[rj + k];
      A[i * P + j] = t / L;
    }
  }
  const th = new Float64Array(P * 3), y = new Float64Array(P);
  for (let ch = 0; ch < 3; ch++) {
    for (let i = 0; i < P; i++) { let t = B[i * 3 + ch]; for (let k = 0; k < i; k++) t -= A[i * P + k] * y[k]; y[i] = t / A[i * P + i]; }
    for (let i = P - 1; i >= 0; i--) { let t = y[i]; for (let k = i + 1; k < P; k++) t -= A[k * P + i] * th[k * 3 + ch]; th[i * 3 + ch] = t / A[i * P + i]; }
  }
  return th;
}

// 한 픽셀 예측 → out[0..2] (0~255 반올림). zr·zg·zb: 격자 입력 0~255
export function predict(spec, th, zr, zg, zb, q, idx, wt, out) {
  const K = corners(spec, q, idx, wt), z0 = zr / 255, z1 = zg / 255, z2 = zb / 255;
  for (let ch = 0; ch < 3; ch++) {
    let s = 0;
    for (let a = 0; a < K; a++) { const o = idx[a] * 4; s += wt[a] * (th[o * 3 + ch] * z0 + th[(o + 1) * 3 + ch] * z1 + th[(o + 2) * 3 + ch] * z2 + th[(o + 3) * 3 + ch] * 1); }
    out[ch] = Math.round(Math.min(255, Math.max(0, s * 255)));
  }
}
