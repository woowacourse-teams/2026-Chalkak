// 연구 코드에서 가져온 작은 수치 도구: 선형 풀이, 평활(PAVA), 보간. 연구 코드와 같은 식·같은 문턱을 쓴다(결과 숫자가 같아야 한다)

// 가우스-조던(부분 피벗). 비네팅 읽기의 가중 최소제곱이 쓴다
function solve(A, b) {
  const n = b.length, M = A.map((row, i) => [...row, b[i]]);
  for (let c = 0; c < n; c++) {
    let p = c;
    for (let i = c + 1; i < n; i++) if (Math.abs(M[i][c]) > Math.abs(M[p][c])) p = i;
    [M[c], M[p]] = [M[p], M[c]];
    if (Math.abs(M[c][c]) < 1e-12) continue;
    for (let i = 0; i < n; i++) if (i !== c) { const f = M[i][c] / M[c][c]; for (let k = c; k <= n; k++) M[i][k] -= f * M[c][k]; }
  }
  return M.map((row, i) => (Math.abs(row[i]) < 1e-12 ? 0 : row[n] / row[i]));
}

export function lsq(rows, ys, ws) {
  const m = rows[0].length, A = Array.from({ length: m }, () => new Array(m).fill(0)), b = new Array(m).fill(0);
  rows.forEach((f, i) => { for (let p = 0; p < m; p++) { b[p] += ws[i] * f[p] * ys[i]; for (let q = 0; q < m; q++) A[p][q] += ws[i] * f[p] * f[q]; } });
  return solve(A, b);
}

// 가우스-조던(문턱 1e-15). 비네팅 밝기 응답 표(F) 맞춤이 쓴다
export function solveSym(A, b) {
  const n = b.length, M = A.map((row, i) => [...row, b[i]]);
  for (let c = 0; c < n; c++) {
    let p = c;
    for (let i = c + 1; i < n; i++) if (Math.abs(M[i][c]) > Math.abs(M[p][c])) p = i;
    [M[c], M[p]] = [M[p], M[c]];
    if (Math.abs(M[c][c]) < 1e-15) continue;
    for (let i = 0; i < n; i++) if (i !== c) { const f = M[i][c] / M[c][c]; for (let k = c; k <= n; k++) M[i][k] -= f * M[c][k]; }
  }
  return M.map((row, i) => (Math.abs(row[i]) < 1e-15 ? 0 : row[n] / row[i]));
}

// 대칭 양정치 행렬 풀이(촐레스키). 지역 톤·대역 이득 표 맞춤이 쓴다
export function cholSolve(A, b, m) {
  const Lw = new Float64Array(m * m);
  for (let i = 0; i < m; i++) for (let j = 0; j <= i; j++) {
    let s = A[i * m + j];
    for (let k = 0; k < j; k++) s -= Lw[i * m + k] * Lw[j * m + k];
    Lw[i * m + j] = i === j ? Math.sqrt(Math.max(s, 1e-12)) : s / Lw[j * m + j];
  }
  const y = new Float64Array(m), x = new Float64Array(m);
  for (let i = 0; i < m; i++) { let s = b[i]; for (let k = 0; k < i; k++) s -= Lw[i * m + k] * y[k]; y[i] = s / Lw[i * m + i]; }
  for (let i = m - 1; i >= 0; i--) { let s = y[i]; for (let k = i + 1; k < m; k++) s -= Lw[k * m + i] * x[k]; x[i] = s / Lw[i * m + i]; }
  return x;
}

export const median = (xs) => {
  if (!xs.length) return 0;
  const s = Float64Array.from(xs).sort(), m = s.length >> 1;
  return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2;
};

// 가중 PAVA: 반경이 커질수록 같거나 줄어드는 수열로 편다
export function isoDecreasing(ys, ws) {
  const blocks = [];
  ys.forEach((v, i) => {
    blocks.push({ v, w: ws[i], n: 1 });
    while (blocks.length > 1 && blocks[blocks.length - 2].v < blocks[blocks.length - 1].v) {
      const b = blocks.pop(), a = blocks.pop();
      blocks.push({ v: (a.v * a.w + b.v * b.w) / (a.w + b.w), w: a.w + b.w, n: a.n + b.n });
    }
  });
  return blocks.flatMap((b) => new Array(b.n).fill(b.v));
}

// 구간 선형 보간(양 끝은 끝 값 유지)
export function interp(xs, ys, x) {
  if (x <= xs[0]) return ys[0];
  const n = xs.length;
  if (x >= xs[n - 1]) return ys[n - 1];
  let k = 0;
  while (x > xs[k + 1]) k++;
  return ys[k] + ((ys[k + 1] - ys[k]) * (x - xs[k])) / (xs[k + 1] - xs[k]);
}

export const mean = (a) => a.reduce((s, v) => s + v, 0) / a.length;
export const clamp = (v, lo, hi) => (v < lo ? lo : v > hi ? hi : v);
