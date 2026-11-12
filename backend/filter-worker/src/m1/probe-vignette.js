// 첫 번째 비네팅 읽기(견본 v2): 코어 회색 기준칸과 바탕 부채꼴에서 밝기 단계별 비네팅 표를 맞추고 그 표로 견본을 되돌린다.
// 이 결과는 LUT 첫 추정(비네팅 하위 설정을 읽기 전의 기준 무편집 모양)을 얻는 데만 쓴다. 비네팅 값 V는 vignette.js가 두 번째 단계로 읽는다.
//
// 비네팅 V(r, v): 입력 견본을 정확히 알기 때문에 막는 대신 재서 되돌린다.
//   코어 회색 기준칸(입력 128, r 0~0.46): 반경 구간 중앙값 → log V_g(r). r < V_CENTER의 중앙값을 V = 1 기준으로 두고, r에 대해 단조 감소로 편다.
//   바탕(r 0.37 이상, 8가지 밝기): 회색칸과 겹치는 반경에서 log out_k(r) = log T_k + s_k · log V_g(r)로 레벨의 비네팅 전 밝기 T_k와
//   하이라이트 우선 배율 s_k(밝을수록 덜 어두워짐)를 맞춘다. 코어 안은 s_k · log V_g(r), 바깥은 바탕 실측 log out_k − log T_k.
//   보정은 출력 밝기로 레벨 사이를 보간한 V로 선형 RGB를 나눈다. 회색칸이 반경만으로 설명되지 않으면(지역 대비 등) 끈다.
import { LIN, encode, lumLin } from './color.js';
import { BG_L, CELL, CORE, CORE_COLS, CORE_ROWS, H, LAYOUT, W, regionMask, sectorOf } from './layout.js';
import { interp, isoDecreasing, lsq, median } from './numeric.js';

const R_BIN = 0.025, NRB = 40; // 타원 정규 반경 구간 (모서리 1)
const V_CENTER = 0.1; // 이 반경 안 회색칸의 중앙값을 V = 1 기준으로 둔다
const V_MIN = 0.05; // 이보다 어두워진 반경(구석)은 맞춤에 쓰지 않고, 보정도 이 값까지만 한다
const V_MIN_SIGNAL = 0.01; // 회색칸 log V 최저값의 크기가 이보다 작으면 비네팅 없음으로 본다
const V_MAX_NOISE = 0.25; // 회색칸 잔차(견고 표준편차)가 신호의 이 비율을 넘으면 반경 모형이 아니라고 보고 끈다
const V_MIN_OVERLAP = 0.3; // 바탕과 겹치는 반경(0.37~0.46)에서 회색칸 log V가 신호의 이 비율 이상 변해야 한다
const EDGE_MARGIN = 6; // 부채꼴 경계에서 이만큼(px) 떨어진 바탕 픽셀만 쓴다

const ellipseR = (x, y) => Math.hypot((x - (W - 1) / 2) / (W / 2), (y - (H - 1) / 2) / (H / 2)) / Math.SQRT2;
const rBin = (r) => Math.min(NRB - 1, Math.floor(r / R_BIN));
// 픽셀 반경은 연구 코드가 Float32 평면에 담던 값과 같게 Float32로 반올림한다(구간 경계 판정이 같아야 한다)
const pixelR = (x, y) => Math.fround(Math.hypot((x - (W - 1) / 2) / (W / 2), (y - (H - 1) / 2) / (H / 2)) / Math.SQRT2);

// 회색 기준칸 목록(코어 안 c < 0인 칸)
function grayCells() {
  const out = [];
  for (let cy = 0; cy < CORE_ROWS; cy++) for (let cx = 0; cx < CORE_COLS; cx++) {
    if (LAYOUT.coreGrid[cy * CORE_COLS + cx] < 0) out.push({ cx, cy, r: ellipseR(CORE.x + cx * CELL + 3.5, CORE.y + cy * CELL + 3.5) });
  }
  return out;
}

function cellMean(rgba, cx, cy) {
  const x0 = CORE.x + cx * CELL + 2, y0 = CORE.y + cy * CELL + 2, out = [0, 0, 0];
  for (let y = y0; y < y0 + 4; y++) for (let x = x0; x < x0 + 4; x++) { const p = (y * W + x) * 4; out[0] += rgba[p]; out[1] += rgba[p + 1]; out[2] += rgba[p + 2]; }
  return out.map((v) => v / 16);
}
const linF = (v) => { const a = Math.floor(v), f = v - a; return a >= 255 ? LIN[255] : LIN[a] * (1 - f) + LIN[a + 1] * f; };
const lumF = (m) => 0.2126729 * linF(m[0]) + 0.7151522 * linF(m[1]) + 0.072175 * linF(m[2]);

// 비네팅 표 맞춤. applied가 false면 table은 null이다
export function fitProbeVignette(rgba) {
  // 1) 회색 기준칸 → log V_g(r)
  const gc = grayCells(), ly = gc.map(({ cx, cy }) => Math.log(Math.max(1e-6, lumF(cellMean(rgba, cx, cy)))));
  const ref = median(ly.filter((_, j) => gc[j].r < V_CENTER));
  const gb = Array.from({ length: NRB }, () => ({ v: [], r: 0 }));
  gc.forEach((c, j) => { const b = gb[rBin(c.r)]; b.v.push(ly[j] - ref); b.r += c.r; });
  const gk = gb.map((b, k) => ({ k, n: b.v.length, r: b.v.length ? b.r / b.v.length : 0, m: median(b.v) })).filter((b) => b.n >= 8);
  const iso = isoDecreasing(gk.map((b) => b.m), gk.map((b) => b.n)).map((v) => Math.min(0, v));
  const gR = [0, ...gk.map((b) => b.r)], gLV = [0, ...iso], gMax = gR[gR.length - 1];
  const grayLV = (r) => interp(gR, gLV, r);
  const res = gc.map((c, j) => ly[j] - ref - grayLV(c.r)), mres = median(res);
  const noise = 1.4826 * median(res.map((e) => Math.abs(e - mres))), signal = -Math.min(...gLV);

  // 2) 바탕: 레벨 × 반경 구간의 log Y 평균
  const nl = BG_L.length, sumLog = new Float64Array(nl * NRB), cnt = new Float64Array(nl * NRB), mask = regionMask(), sec = { level: 0, edgeDist: 0 };
  for (let y = 4; y < H - 4; y++) for (let x = 4; x < W - 4; x++) {
    const i = y * W + x;
    if (mask[i]) continue;
    sectorOf(x, y, sec);
    if (sec.edgeDist < EDGE_MARGIN) continue;
    const p = i * 4, c = sec.level * NRB + rBin(pixelR(x, y));
    sumLog[c] += Math.log(Math.max(1e-6, lumLin(rgba[p], rgba[p + 1], rgba[p + 2]))); cnt[c]++;
  }
  const rc = Array.from({ length: NRB }, (_, b) => (b + 0.5) * R_BIN);
  let reason = null;
  if (signal < V_MIN_SIGNAL) reason = '비네팅 없음';
  else if (noise > V_MAX_NOISE * signal) reason = '반경만으로 설명되지 않음';

  // 3) 레벨별 T_k, s_k: 회색칸과 바탕이 겹치는 반경 구간에서 맞춤
  const levels = [];
  for (let k = 0; k < nl; k++) {
    const rows = [], ys = [], ws = [];
    for (let b = 0; b < NRB; b++) {
      const c = k * NRB + b;
      if (cnt[c] < 50 || rc[b] > gMax) continue;
      rows.push([1, grayLV(rc[b])]); ys.push(sumLog[c] / cnt[c]); ws.push(cnt[c]);
    }
    let logT = null, sk = 1;
    if (rows.length) {
      const span = Math.max(...rows.map((f) => f[1])) - Math.min(...rows.map((f) => f[1]));
      if (span >= 0.02 && rows.length >= 2) sk = Math.min(2, Math.max(0, lsq(rows, ys, ws)[1]));
      const wsum = ws.reduce((a, b) => a + b, 0);
      logT = rows.reduce((a, f, i) => a + ws[i] * (ys[i] - sk * f[1]), 0) / wsum;
    }
    levels.push({ L: BG_L[k], logT, s: sk });
  }
  if (!reason && levels.some((l) => l.logT === null)) reason = '바탕과 회색칸이 겹치는 반경이 없음';
  const span = -grayLV(gMax) + grayLV(Math.min(...rc.filter((r, b) => r <= gMax && cnt[b] >= 50)));
  if (!reason && !(span >= V_MIN_OVERLAP * signal)) reason = '바탕이 뒷받침하지 않음';
  if (reason) return { applied: false, reason, signal, noise, levels, rc, table: null };

  // 4) 레벨 × 반경 표 log V_k(r): 코어 반경은 s_k · log V_g, 바깥은 바탕 실측. 단조 감소로 펴고 V_MIN 아래는 맞춤에서 뺀다
  const table = [];
  for (let k = 0; k < nl; k++) {
    const lv = levels[k], xs = [0], ys = [0], ws = [1e6];
    for (let b = 0; b < NRB; b++) {
      const c = k * NRB + b;
      let v = null, w = 0;
      if (rc[b] <= gMax) { v = lv.s * grayLV(rc[b]); w = gb[b].v.length + 1; }
      else if (cnt[c] >= 50) { v = sumLog[c] / cnt[c] - lv.logT; w = cnt[c]; }
      if (v === null || v < Math.log(V_MIN)) continue;
      xs.push(rc[b]); ys.push(v); ws.push(w);
    }
    const iy = isoDecreasing(ys, ws).map((v) => Math.min(0, v));
    table.push(rc.map((r) => (r > xs[xs.length - 1] ? Math.log(V_MIN) : interp(xs, iy, r))));
  }
  return { applied: true, reason: null, signal, noise, levels, rc, table };
}

// 출력 밝기로 레벨 사이를 보간한 V로 선형 RGB를 나눈다. 새 RGBA를 돌려준다(적용 안 됨이면 입력을 그대로)
export function correctProbeVignette(rgba, model) {
  if (!model.applied) return rgba;
  const out = new Uint8ClampedArray(rgba.length), nl = model.levels.length, NR = 1024;
  const lo = new Float64Array(NR * nl), lv = new Float64Array(NR * nl);
  for (let q = 0; q < NR; q++) {
    const rr = q / (NR - 1), ord = model.levels.map((L, k) => ({ g: interp(model.rc, model.table[k], rr), T: L.logT })).sort((a, b) => a.T + a.g - (b.T + b.g));
    ord.forEach((o, k) => { lv[q * nl + k] = o.g; lo[q * nl + k] = o.T + o.g; });
  }
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, p = i * 4, q = Math.min(NR - 1, Math.round(pixelR(x, y) * (NR - 1))) * nl;
    const ly = Math.log(Math.max(1e-6, lumLin(rgba[p], rgba[p + 1], rgba[p + 2])));
    let g;
    if (ly <= lo[q]) g = lv[q];
    else if (ly >= lo[q + nl - 1]) g = lv[q + nl - 1];
    else {
      let k = 0;
      while (k < nl - 2 && ly > lo[q + k + 1]) k++;
      const f = (ly - lo[q + k]) / Math.max(1e-9, lo[q + k + 1] - lo[q + k]);
      g = lv[q + k] * (1 - f) + lv[q + k + 1] * f;
    }
    const inv = Math.exp(-g);
    for (let ch = 0; ch < 3; ch++) out[p + ch] = encode(LIN[rgba[p + ch]] * inv);
    out[p + 3] = 255;
  }
  return out;
}
