// 비네팅 V 읽기: 무편집 견본과의 비교로 형태 거리 d(p, c)·반경 곡선 U(d)·밝기 응답 F_v를 함께 읽고, 읽은 비네팅을 견본에서 되돌린다.
//   - 기준은 합성 견본 값이 아니라 "LUT만 건 무편집 모양"이다. 비네팅은 LUT 결과에 걸리므로 ln(Y_조건 / Y_무편집)이 연산자가 재현해야 할 양이다.
//   - 바탕 부채꼴만이 아니라 견본의 균일한 회색 영역(완충띠 L* 50, 코어 회색칸, 평탄 패치, 톤 계단)을 모두 쓴다. 그래서 r < 0.36 안쪽도 잰다.
//   - 반경을 타원으로 고정하지 않고 초타원 d = (|x_n|^p + (c|y_n|)^p)^(1/p)의 (p, c)를 견본에서 고른다.
// 연산자 정의(표 T + 형태 거리 + 이득 조회)도 여기에 있다. 앱에 새로 넣을 연산자와 같은 식이다.
import { LIN, encode, lstar, lumLin } from './color.js';
import { H, SECTORS, W, inputProbe, regionMask } from './layout.js';
import { clamp, solveSym } from './numeric.js';

// ---------------------------------------------------------------- 0. 연산자 (표 T + 형태 거리)
//   vig = { nr, nv, vKnots, mode, T, shape }
//     nr 점의 형태 거리(d = i / (nr − 1), 모서리 1) × nv 점의 입력 밝기 L*(vKnots)에 대한 표 T[k * nr + i].
//     mode 'dark' : T = ln 이득(≤ 0). 선형 RGB에 exp(T)를 곱한다.
//     mode 'light': T = 흰색으로 섞는 양 a(0~1). 선형 RGB를 c + (1 − c) · a로 바꾼다.
//   정규 좌표 x_n = dx / (W/2), y_n = dy / (H/2) (프레임 가장자리에서 ±1)
//   d = (|x_n|^p + (c·|y_n|)^p)^(1/p) / (1 + c^p)^(1/p) → 모서리에서 1. shape가 없으면 타원(p = 2, c = 1).
export const ELLIPSE = { p: 2, c: 1 };
export const VIG_V_KNOTS = [10, 22, 34, 46, 58, 70, 82, 94];

const isEllipse = (s) => !s || (s.p === 2 && s.c === 1);
export function shapeDist(xn, yn, shape) {
  if (isEllipse(shape)) return Math.hypot(xn, yn) / Math.SQRT2;
  const { p, c } = shape, ax = Math.abs(xn), ay = c * Math.abs(yn);
  if (p === 2) return Math.hypot(ax, ay) / Math.hypot(1, c);
  return (ax ** p + ay ** p) ** (1 / p) / (1 + c ** p) ** (1 / p);
}

// F = { uKnots(오름차순), vals[k][j] }. U가 매듭 밖이면 끝 두 매듭의 기울기로 이어 간다
function vignetteFromPhase(U, F, mode, vKnots = VIG_V_KNOTS) {
  const nr = U.length, nv = F.vals.length, T = new Float64Array(nv * nr), xs = F.uKnots, m = xs.length;
  for (let k = 0; k < nv; k++) for (let i = 0; i < nr; i++) {
    const x = U[i];
    let j = 0;
    while (j < m - 2 && x >= xs[j + 1]) j++;
    const t = F.vals[k][j] + ((F.vals[k][j + 1] - F.vals[k][j]) * (x - xs[j])) / (xs[j + 1] - xs[j]);
    T[k * nr + i] = mode === 'light' ? clamp(t, 0, 1) : Math.min(0, t);
  }
  return { nr, nv, vKnots, mode, T };
}

export function vignetteGainAt(vig, r, v) {
  const { nr, nv, vKnots, T } = vig, tr = clamp(r, 0, 1) * (nr - 1), i0 = Math.min(nr - 2, Math.floor(tr)), fr = tr - i0;
  const step = (vKnots[nv - 1] - vKnots[0]) / (nv - 1), tv = clamp((v - vKnots[0]) / step, 0, nv - 1), k0 = Math.min(nv - 2, Math.floor(tv)), fv = tv - k0;
  const a = k0 * nr + i0, b = a + nr;
  return (T[a] * (1 - fr) + T[a + 1] * fr) * (1 - fv) + (T[b] * (1 - fr) + T[b + 1] * fr) * fv;
}

// 읽은 비네팅을 견본에서 되돌린다(LUT가 아직 걸리지 않은 무편집 쪽이 아니라 편집본 쪽에서 나눔). 새 RGBA를 돌려준다
export function invertVignette(rgba, w, h, vig) {
  const out = new Uint8ClampedArray(rgba.length), dark = vig.mode !== 'light', shape = vig.shape || ELLIPSE;
  for (let y = 0; y < h; y++) {
    const yn = (y - (h - 1) / 2) / (h / 2);
    for (let x = 0; x < w; x++) {
      const p = (y * w + x) * 4, d = shapeDist((x - (w - 1) / 2) / (w / 2), yn, shape);
      const lr = LIN[rgba[p]], lg = LIN[rgba[p + 1]], lb = LIN[rgba[p + 2]], Y = 0.2126729 * lr + 0.7151522 * lg + 0.072175 * lb;
      let Yin = Y, t = 0;
      for (let it = 0; it < 3; it++) { t = vignetteGainAt(vig, d, lstar(Yin)); Yin = dark ? Y * Math.exp(-t) : (Y - t) / Math.max(1e-3, 1 - t); }
      if (dark) { const g = Math.exp(-t); out[p] = encode(lr * g); out[p + 1] = encode(lg * g); out[p + 2] = encode(lb * g); }
      else { const k = 1 / Math.max(1e-3, 1 - t); out[p] = encode((lr - t) * k); out[p + 1] = encode((lg - t) * k); out[p + 2] = encode((lb - t) * k); }
      out[p + 3] = 255;
    }
  }
  return out;
}

// ---------------------------------------------------------------- 1. 견본 마스크 (합성 견본에서 한 번만)
const BW = W >> 3;
export const NRS = 128; // 형태 연산자 표의 거리 매듭 수
const MID_L = [44, 60]; // "중간 밝기" 표본: 무편집 L*가 이 범위인 칸(바탕 46·58, 완충띠 50, 코어 회색칸 ≈ 54). U(d)는 이 표본의 이득이다
const CODE_LO = 5, CODE_HI = 250; // 검정·흰색 포화 칸(마커 등)은 뺀다
const FLOOR_Y = LIN[3]; // 출력 평균이 코드 3 휘도보다 어두우면 양자화로 이득을 믿을 수 없다
const PHASE_KNOTS = {
  dark: [-7, -5, -3.5, -2.5, -1.8, -1.25, -0.85, -0.55, -0.3, -0.12, 0],
  light: [0, 0.02, 0.05, 0.1, 0.18, 0.28, 0.4, 0.55, 0.75, 1],
};

// 균일 칸: 7×7 창 안이 모두 같은 회색 코드인 픽셀. 부채꼴 경계·패널 가장자리·주파수 패널·색 칸(회색이 아닌 것)은 자동으로 빠진다
let maskCache = null;
function probeMasks() {
  if (maskCache) return maskCache;
  const d = inputProbe(), n = W * H, code = new Int16Array(n);
  for (let i = 0; i < n; i++) { const p = i * 4; code[i] = d[p] === d[p + 1] && d[p] === d[p + 2] ? d[p] : -1; }
  const K = new Int16Array(n).fill(-1);
  const left = new Int32Array(W), right = new Int32Array(W);
  for (let y = 0; y < H; y++) {
    const o = y * W;
    for (let x = 0; x < W; x++) left[x] = x > 0 && code[o + x] === code[o + x - 1] ? left[x - 1] + 1 : 1;
    for (let x = W - 1; x >= 0; x--) right[x] = x < W - 1 && code[o + x] === code[o + x + 1] ? right[x + 1] + 1 : 1;
    for (let x = 0; x < W; x++) if (code[o + x] >= 0 && left[x] >= 4 && right[x] >= 4) K[o + x] = code[o + x];
  }
  const uni = new Uint8Array(n), up = new Int32Array(H), dn = new Int32Array(H);
  for (let x = 0; x < W; x++) {
    for (let y = 0; y < H; y++) up[y] = y > 0 && K[y * W + x] === K[(y - 1) * W + x] ? up[y - 1] + 1 : 1;
    for (let y = H - 1; y >= 0; y--) dn[y] = y < H - 1 && K[y * W + x] === K[(y + 1) * W + x] ? dn[y + 1] + 1 : 1;
    for (let y = 0; y < H; y++) { const i = y * W + x; if (K[i] >= 0 && up[y] >= 4 && dn[y] >= 4) uni[i] = 1; }
  }
  return (maskCache = { code, uni, bg: regionMask() }); // bg: regionMask (0 = 바탕 부채꼴, 1 = 영역·완충띠)
}
export function clearVignetteCaches() { maskCache = null; }

// ---------------------------------------------------------------- 2. 표본: (8×8 블록, 입력 코드)마다 평균
// 반환 열: xn·yn(정규 좌표, 가장자리 ±1), code, n(픽셀 수), Yi·Yc(무편집·조건 선형 휘도 평균), vin(무편집 L*), bg(바탕 부채꼴이면 1), sector(바탕만, 0~119)
export function extractSamples(identRgba, condRgba) {
  const { code, uni, bg: region } = probeMasks(), map = new Map();
  let cap = 1 << 19, m = 0;
  let sx = new Float64Array(cap), sy = new Float64Array(cap), si = new Float64Array(cap), sc = new Float64Array(cap), sn = new Float64Array(cap), sb = new Float64Array(cap), sk = new Int16Array(cap);
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, c = code[i];
    if (!uni[i] || c < CODE_LO || c > CODE_HI) continue;
    const key = ((y >> 3) * BW + (x >> 3)) * 256 + c;
    let j = map.get(key);
    if (j === undefined) {
      j = m++; map.set(key, j);
      if (m > cap) { cap *= 2; const g = (a, T) => { const b = new T(cap); b.set(a); return b; }; sx = g(sx, Float64Array); sy = g(sy, Float64Array); si = g(si, Float64Array); sc = g(sc, Float64Array); sn = g(sn, Float64Array); sb = g(sb, Float64Array); sk = g(sk, Int16Array); }
      sk[j] = c;
    }
    const p = i * 4;
    sx[j] += x; sy[j] += y; sn[j]++; sb[j] += region[i] ? 0 : 1; // regionMask: 0 = 바탕
    si[j] += lumLin(identRgba[p], identRgba[p + 1], identRgba[p + 2]); sc[j] += lumLin(condRgba[p], condRgba[p + 1], condRgba[p + 2]);
  }
  const S = { m, xn: new Float64Array(m), yn: new Float64Array(m), code: sk.slice(0, m), n: sn.slice(0, m), Yi: new Float64Array(m), Yc: new Float64Array(m), vin: new Float64Array(m), bg: new Uint8Array(m), sector: new Int16Array(m).fill(-1) };
  for (let j = 0; j < m; j++) {
    const mx = sx[j] / sn[j], my = sy[j] / sn[j];
    S.xn[j] = (mx - (W - 1) / 2) / (W / 2); S.yn[j] = (my - (H - 1) / 2) / (H / 2);
    S.Yi[j] = si[j] / sn[j]; S.Yc[j] = sc[j] / sn[j]; S.vin[j] = lstar(S.Yi[j]);
    if (sb[j] === sn[j]) { S.bg[j] = 1; const t = (Math.atan2((my + 0.5 - H / 2) / (H / 2), (mx + 0.5 - W / 2) / (W / 2)) + Math.PI) / ((2 * Math.PI) / SECTORS); S.sector[j] = Math.min(SECTORS - 1, Math.floor(t)); }
  }
  return S;
}

// 표본 이득: dark = ln(Yc / Yi), light = 흰색 섞음 a = (Yc − Yi) / (1 − Yi). 못 믿는 칸은 NaN
export function gains(S, mode) {
  const g = new Float64Array(S.m);
  for (let j = 0; j < S.m; j++) {
    if (mode === 'light') g[j] = S.Yc[j] >= LIN[254] ? NaN : (S.Yc[j] - S.Yi[j]) / (1 - S.Yi[j]);
    else g[j] = S.Yc[j] < FLOOR_Y ? NaN : Math.log(S.Yc[j] / S.Yi[j]);
  }
  return g;
}
const isMid = (S, j) => S.vin[j] >= MID_L[0] && S.vin[j] <= MID_L[1];
function modeOf(S) {
  let s = 0, w = 0;
  for (let j = 0; j < S.m; j++) if (isMid(S, j)) { s += S.n[j] * Math.log(Math.max(1e-9, S.Yc[j]) / S.Yi[j]); w += S.n[j]; }
  return s / w > 0 ? 'light' : 'dark';
}

// ---------------------------------------------------------------- 3. 신호 크기·작동 영역
// 극값 g_ext: 중간 밝기 표본 이득의 극단 2% 분위(가장 강하게 걸린 곳). 작동 영역: |g| > 0.1 |g_ext|인 표본
function activity(S, g, mode) {
  const mid = [];
  for (let j = 0; j < S.m; j++) if (isMid(S, j) && Number.isFinite(g[j])) mid.push(g[j]);
  mid.sort((a, b) => a - b);
  const ext = mode === 'light' ? mid[Math.floor(mid.length * 0.98)] : mid[Math.floor(mid.length * 0.02)];
  const thr = 0.1 * Math.abs(ext), sectors = new Set();
  let blocks = 0;
  for (let j = 0; j < S.m; j++) {
    if (!Number.isFinite(g[j]) || Math.abs(g[j]) <= thr) continue;
    blocks++;
    if (S.bg[j]) sectors.add(S.sector[j]);
  }
  return { ext, thr, blocks, sectors: sectors.size };
}

// ---------------------------------------------------------------- 4. 형태 고르기: 입력 코드마다 이득이 d 하나의 함수인지 (F를 몰라도 된다)
// 잔차 = (코드, d 구간)별 가중 분산의 합의 제곱근
const DBINS = 192;
function shapeResidual(S, g, shape, sel = null) {
  const nc = 256, sum = new Float64Array(nc * DBINS), sq = new Float64Array(nc * DBINS), wt = new Float64Array(nc * DBINS);
  let tw = 0;
  for (let j = 0; j < S.m; j++) {
    if (!Number.isFinite(g[j]) || (sel && !sel[j])) continue;
    const d = shapeDist(S.xn[j], S.yn[j], shape), c = S.code[j] * DBINS + Math.min(DBINS - 1, Math.floor(d * DBINS)), w = S.n[j];
    sum[c] += w * g[j]; sq[c] += w * g[j] * g[j]; wt[c] += w; tw += w;
  }
  let se = 0;
  for (let c = 0; c < wt.length; c++) if (wt[c] > 0) se += sq[c] - (sum[c] * sum[c]) / wt[c];
  return Math.sqrt(Math.max(0, se) / tw);
}
const SCAN_P = [1.5, 1.75, 2, 2.5, 3, 4, 5, 6, 8, 10, 14, 20];
const SCAN_C = [0.7, 0.8, 0.9, 1, 1.1, 1.2, 4 / 3, 1.45, 1.6];
// 작동 영역 표본만으로 격자 탐색 → (log p, c) 좌표 하강으로 다듬기
function fitVignetteShape(S, g, act) {
  const sel = Uint8Array.from({ length: S.m }, (_, j) => (Number.isFinite(g[j]) && Math.abs(g[j]) > act.thr * 0.5 ? 1 : 0));
  // 작동 영역에 이웃한 약한 표본도 넣어야 "어디서 시작하는지"가 잡힌다: 작동 표본의 최소 타원 반경 − 0.1 안쪽까지 넓힌다
  let dmin = 1;
  for (let j = 0; j < S.m; j++) if (sel[j]) dmin = Math.min(dmin, Math.hypot(S.xn[j], S.yn[j]) / Math.SQRT2);
  for (let j = 0; j < S.m; j++) if (Number.isFinite(g[j]) && Math.hypot(S.xn[j], S.yn[j]) / Math.SQRT2 >= dmin - 0.1) sel[j] = 1;
  const grid = [];
  for (const p of SCAN_P) for (const c of SCAN_C) grid.push({ p, c, res: shapeResidual(S, g, { p, c }, sel) });
  let best = grid.reduce((a, b) => (b.res < a.res ? b : a));
  let lp = Math.log(best.p), c = best.c, cur = best.res, sp = 0.15, scs = 0.04;
  for (let it = 0; it < 30 && (sp > 0.01 || scs > 0.003); it++) {
    let moved = false;
    for (const [dl, dc] of [[sp, 0], [-sp, 0], [0, scs], [0, -scs]]) {
      const cand = { p: Math.exp(lp + dl), c: c + dc };
      if (cand.p < 1.2 || cand.p > 40 || cand.c < 0.5 || cand.c > 2) continue;
      const r = shapeResidual(S, g, cand, sel);
      if (r < cur) { cur = r; lp += dl; c += dc; moved = true; }
    }
    if (!moved) { sp /= 2; scs /= 2; }
  }
  best = { p: Math.exp(lp), c, res: cur };
  return { best, ellipse: shapeResidual(S, g, ELLIPSE, sel) };
}

// ---------------------------------------------------------------- 5. U(d)와 F_v
// U(d): 중간 밝기 표본의 이득을 NRS 매듭(가장 가까운 매듭 구간)으로 평균 → 단조(dark: 줄어듦, light: 늘어남)로 편다(PAVA). 빈 매듭은 이웃 보간, 바깥은 끝 값 유지
function pava(ys, ws, dir) {
  const blocks = [];
  ys.forEach((v0, i) => {
    const v = dir * v0;
    blocks.push({ v, w: ws[i], n: 1 });
    while (blocks.length > 1 && blocks[blocks.length - 2].v < blocks[blocks.length - 1].v) {
      const b = blocks.pop(), a = blocks.pop();
      blocks.push({ v: (a.v * a.w + b.v * b.w) / (a.w + b.w), w: a.w + b.w, n: a.n + b.n });
    }
  });
  return blocks.flatMap((b) => new Array(b.n).fill(dir * b.v));
}
function fitU(S, g, mode, shape, nr = NRS) {
  const sum = new Float64Array(nr), wt = new Float64Array(nr);
  for (let j = 0; j < S.m; j++) {
    if (!isMid(S, j) || !Number.isFinite(g[j])) continue;
    const i = Math.round(shapeDist(S.xn[j], S.yn[j], shape) * (nr - 1));
    sum[i] += S.n[j] * g[j]; wt[i] += S.n[j];
  }
  const idx = [], ys = [], ws = [];
  for (let i = 0; i < nr; i++) if (wt[i] >= 16) { idx.push(i); ys.push(sum[i] / wt[i]); ws.push(wt[i]); }
  // dark는 반경이 커질수록 같거나 줄어드는 수열(PAVA 방향 +1: 앞이 뒤보다 크거나 같게), light는 늘어나는 수열
  const iso = pava(ys, ws, mode === 'light' ? -1 : 1);
  const U = new Float64Array(nr);
  for (let i = 0; i < nr; i++) {
    let k = 0;
    while (k < idx.length - 1 && idx[k + 1] <= i) k++;
    if (i <= idx[0]) U[i] = iso[0];
    else if (i >= idx[idx.length - 1]) U[i] = iso[iso.length - 1];
    else U[i] = iso[k] + ((iso[k + 1] - iso[k]) * (i - idx[k])) / (idx[k + 1] - idx[k]);
  }
  return U;
}

const uAt = (U, d) => { const t = Math.min(1, Math.max(0, d)) * (U.length - 1), i0 = Math.min(U.length - 2, Math.floor(t)), f = t - i0; return U[i0] * (1 - f) + U[i0 + 1] * f; };

// F[k][m]: v 매듭(VIG_V_KNOTS) × U 매듭(PHASE_KNOTS) 이중선형 표를 가중 최소제곱으로.
// F(v, 0) = 0은 큰 가중으로 묶고, 빈 칸은 두 축 2차 차분 평활로 메운다
function fitF(S, g, U, shape, mode) {
  const vk = VIG_V_KNOTS, nv = vk.length, xs = PHASE_KNOTS[mode], m = xs.length, N = nv * m, step = (vk[nv - 1] - vk[0]) / (nv - 1);
  const A = Array.from({ length: N }, () => new Float64Array(N)), b = new Float64Array(N);
  let wsum = 0;
  const add = (terms, y, w) => { for (const [p, hp] of terms) { b[p] += w * hp * y; for (const [q, hq] of terms) A[p][q] += w * hp * hq; } };
  const basis = (v, u) => {
    const tv = Math.min(nv - 1, Math.max(0, (v - vk[0]) / step)), k0 = Math.min(nv - 2, Math.floor(tv)), fv = tv - k0;
    let j = 0;
    while (j < m - 2 && u >= xs[j + 1]) j++;
    const fu = Math.min(1, Math.max(0, (u - xs[j]) / (xs[j + 1] - xs[j])));
    return [[k0 * m + j, (1 - fv) * (1 - fu)], [k0 * m + j + 1, (1 - fv) * fu], [(k0 + 1) * m + j, fv * (1 - fu)], [(k0 + 1) * m + j + 1, fv * fu]];
  };
  for (let j = 0; j < S.m; j++) {
    if (!Number.isFinite(g[j])) continue;
    add(basis(S.vin[j], uAt(U, shapeDist(S.xn[j], S.yn[j], shape))), g[j], S.n[j]); wsum += S.n[j];
  }
  const zeroIdx = xs.indexOf(0);
  for (let k = 0; k < nv; k++) add([[k * m + zeroIdx, 1]], 0, wsum);
  const lam = (1e-3 * wsum) / N;
  for (let k = 0; k < nv; k++) for (let j = 1; j < m - 1; j++) add([[k * m + j - 1, 1], [k * m + j, -2], [k * m + j + 1, 1]], 0, lam);
  for (let j = 0; j < m; j++) for (let k = 1; k < nv - 1; k++) add([[(k - 1) * m + j, 1], [k * m + j, -2], [(k + 1) * m + j, 1]], 0, lam);
  for (let p = 0; p < N; p++) A[p][p] += 1e-9 * wsum;
  const sol = solveSym(A.map((r) => Array.from(r)), Array.from(b));
  return { uKnots: xs, vals: Array.from({ length: nv }, (_, k) => sol.slice(k * m, (k + 1) * m)) };
}

// ---------------------------------------------------------------- 6. 견본 한 장 → 연산자 (형태·U·F 모두 읽기)
// 형태 판정 규칙:
//   - 작동 영역 표본이 MIN_ACTIVE 블록 미만이거나 바탕 부채꼴 MIN_SECTORS개 미만이면 형태를 읽지 않는다(측정 불가 → 타원).
//   - 최적 형태의 잔차가 타원 잔차보다 SHAPE_GAIN(상대) 이상 줄지 않으면 타원을 쓴다(형태 차이를 데이터가 뒷받침하지 않음).
const MIN_ACTIVE = 300, MIN_SECTORS = 24, SHAPE_GAIN = 0.1;
export function readShape(S, { shape: fixed = null } = {}) {
  const mode = modeOf(S), g = gains(S, mode), act = activity(S, g, mode);
  const measurable = act.blocks >= MIN_ACTIVE && act.sectors >= MIN_SECTORS;
  let shape = ELLIPSE;
  if (fixed) shape = fixed;
  else if (measurable) {
    const fit = fitVignetteShape(S, g, act);
    if (fit.best.res < (1 - SHAPE_GAIN) * fit.ellipse) shape = { p: fit.best.p, c: fit.best.c };
  }
  const U = fitU(S, g, mode, shape), F = fitF(S, g, U, shape, mode), vig = { ...vignetteFromPhase(U, F, mode), shape: { ...shape } };
  return { mode, act, measurable, shape, U, vig };
}
