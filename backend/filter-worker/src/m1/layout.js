// 견본(3072x4096) 배치와 마커 정합. 배치는 데이터(LAYOUT.regions)로 한 번 정의하고 합성 견본 생성과 읽기가 같은 정의를 쓴다.
// 패널은 사각형 바깥 BUF px을 중성 회색 완충띠로 칠하고, 어떤 패널·완충띠에도 속하지 않는 픽셀이 비네팅 바탕(8가지 밝기 부채꼴)이다.
import { ExtractionFailure } from '../failure.js';
import { encode, rgbToLab } from './color.js';

export const W = 3072;
export const H = 4096;
export const CELL = 8;
export const N = 33;
const BUF = 48;
const REF_GRAY = 128; // 색 코어 회색 기준칸 (RGB 128)
const BUF_L = 50; // 완충띠 L*
// 바탕 부채꼴 수(타원 정규 좌표의 각도). 밝기 단계 = 부채꼴 번호 % 8 → 24°마다 8단계가 한 번씩.
// 사분면당 30개(8의 배수가 아님)라 네 모서리에 서로 다른 단계가 걸려, 마커가 가리는 모서리 끝(r > 0.9)에서도 8단계가 다 나온다
export const SECTORS = 120;
export const BG_L = [10, 22, 34, 46, 58, 70, 82, 94];
const TONE_STEPS = 32;
const LOC_CENTER_L = [20, 50, 80], LOC_SURROUND_L = [15, 50, 85], LOC_SIZES = [64, 152, 304], LOC_CENTER = 32; // 주변 크기 = 짧은 변 2/5/10%
const FREQ_PERIODS = [4, 6, 8, 12, 16, 24, 32, 64], FREQ_AMPS = [2, 8, 32], FREQ_MEANS = [30, 55, 80];
const FLAT_L = [5, 20, 35, 50, 65, 80, 90, 97];
const DEHAZE_COLORS = [[220, 40, 40], [220, 200, 30], [40, 170, 60], [30, 180, 200], [50, 70, 220], [200, 50, 190]];
const EDGE_DEG = 5, EDGE_L = [30, 70];

const KAPPA = 24389 / 27;
const yOfL = (L) => (L > 8 ? ((L + 16) / 116) ** 3 : L / KAPPA);
export const grayOfL = (L) => encode(yOfL(L));
export const gridValue = (c) => Math.round((c * 255) / (N - 1));

// 색 코어: 33×33 r-g 타일(b 하나)을 6×6 타일 격자에 행 우선으로 놓고, 홀수 타일 열은 r을, 홀수 타일 행은 g를 거울상으로 뒤집는다.
// 그러면 가로 이웃은 타일 안에서 r 1단계, 타일 경계에서 b 1단계만 다르다. 세로 이웃은 타일 안에서 g 1단계이고,
// 타일 행 경계(b가 6단계 뛰는 곳)에는 회색 기준칸 한 줄을 끼운다. 코어 둘레 한 칸과 빈 타일 3개(b 33~35 자리)도 회색 기준칸이다.
const TILES_X = 6, TILES_Y = 6;
export const CORE_COLS = TILES_X * N + 2, CORE_ROWS = TILES_Y * N + (TILES_Y - 1) + 2; // 200 × 205 칸
function buildCoreGrid() {
  const grid = new Int32Array(CORE_COLS * CORE_ROWS).fill(-1); // -1 = 회색 기준칸, 그 밖은 r + 33g + 33²b
  for (let b = 0; b < N; b++) {
    const TX = b % TILES_X, TY = Math.floor(b / TILES_X);
    for (let g = 0; g < N; g++) for (let r = 0; r < N; r++) {
      const cx = 1 + TX * N + (TX % 2 ? N - 1 - r : r), cy = 1 + TY * (N + 1) + (TY % 2 ? N - 1 - g : g);
      grid[cy * CORE_COLS + cx] = r + g * N + b * N * N;
    }
  }
  return grid;
}

// 영역 목록. 좌표는 모두 8 px 배수(JPEG 8×8 블록과 칸 경계를 맞춘다). 값의 의미는 종류마다 다르다
function buildLayout() {
  const regions = [];
  const add = (r) => { regions.push(r); return r; };
  const coreW = CORE_COLS * CELL, coreH = CORE_ROWS * CELL;
  add({ name: 'core', kind: 'core', x: (W - coreW) / 2, y: 1224, w: coreW, h: coreH });
  for (const [k, x, y] of [['tl', 64, 64], ['tr', W - 160, 64], ['bl', 64, H - 160], ['br', W - 160, H - 160]]) add({ name: `marker-${k}`, kind: 'marker', x, y, w: 96, h: 96, square: 16 });
  // 톤 계단: 코어 바로 위, 32단계 × 52 px
  const tone = add({ name: 'tone', kind: 'tonePanel', x: 704, y: 1048, w: TONE_STEPS * 52, h: 128, steps: [] });
  for (let k = 0; k < TONE_STEPS; k++) tone.steps.push({ name: `tone-${k}`, x: tone.x + k * 52, y: tone.y, w: 52, h: 128, L: (100 * k) / (TONE_STEPS - 1) });
  // 주파수: 왼쪽 위. 열 = 주기, 행 = 평균 L* × 진폭. 칸 128×64, 칸 사이 16 px는 패널 바탕(중성 회색). 패널 전체가 한 영역
  const freq = add({ name: 'freq', kind: 'freqPanel', x: 208, y: 208, w: FREQ_PERIODS.length * 144 - 16, h: FREQ_MEANS.length * FREQ_AMPS.length * 80 - 16, patches: [] });
  FREQ_MEANS.forEach((m, mi) => FREQ_AMPS.forEach((a, ai) => FREQ_PERIODS.forEach((p, pi) => {
    freq.patches.push({ name: `freq-P${p}-A${a}-L${m}`, x: freq.x + pi * 144, y: freq.y + (mi * FREQ_AMPS.length + ai) * 80, w: 128, h: 64, period: p, amp: a, L: m });
  })));
  add({ name: 'edge', kind: 'edge', x: 2560, y: 320, w: 256, h: 256, deg: EDGE_DEG, Ls: EDGE_L });
  // 국소성: 2%(64)는 위 오른쪽 한 줄, 5%(152)는 그 아래 두 줄, 10%(304)는 코어 왼쪽 5개·오른쪽 4개
  const locs = [];
  LOC_CENTER_L.forEach((c) => LOC_SURROUND_L.forEach((s) => locs.push([c, s])));
  locs.forEach(([c, s], i) => add({ name: `loc-2-${c}-${s}`, kind: 'locality', x: 1440 + i * 112, y: 208, w: LOC_SIZES[0], h: LOC_SIZES[0], L: c, surroundL: s, pct: 2 }));
  locs.forEach(([c, s], i) => add({ name: `loc-5-${c}-${s}`, kind: 'locality', x: 1440 + (i % 5) * 200, y: i < 5 ? 320 : 520, w: LOC_SIZES[1], h: LOC_SIZES[1], L: c, surroundL: s, pct: 5 }));
  locs.forEach(([c, s], i) => add({ name: `loc-10-${c}-${s}`, kind: 'locality', x: i < 5 ? 336 : 2432, y: i < 5 ? 1100 + i * 352 : 1276 + (i - 5) * 352, w: LOC_SIZES[2], h: LOC_SIZES[2], L: c, surroundL: s, pct: 10 }));
  // 평탄 패치: 코어 아래 한 줄
  FLAT_L.forEach((L, i) => add({ name: `flat-${L}`, kind: 'flat', x: 344 + i * 304, y: 2912, w: 256, h: 256, L }));
  // 디헤이즈: 왼쪽 채도 색, 오른쪽 같은 L*의 회색 (붙어 있는 한 쌍이 한 영역)
  DEHAZE_COLORS.forEach((rgb, i) => add({ name: `dehaze-${i}`, kind: 'dehaze', x: 840 + i * 240, y: 3232, w: 192, h: 96, rgb }));
  return { width: W, height: H, cell: CELL, buf: BUF, regions, coreGrid: buildCoreGrid(), cols: CORE_COLS, rows: CORE_ROWS };
}
export const LAYOUT = buildLayout();
export const CORE = LAYOUT.regions[0];
export const MARKERS = LAYOUT.regions.filter((r) => r.kind === 'marker');
export const FLATS = LAYOUT.regions.filter((r) => r.kind === 'flat');

// 겹침·범위 점검: 모든 영역이 캔버스 안에 있고, 두 영역 사이에 BUF 이상 간격이 있는지 (위반 목록을 돌려준다)
export function checkLayout(layout = LAYOUT) {
  const bad = [], rs = layout.regions;
  for (const r of rs) if (r.x - layout.buf < 0 || r.y - layout.buf < 0 || r.x + r.w + layout.buf > layout.width || r.y + r.h + layout.buf > layout.height) bad.push(`${r.name}: 캔버스 밖(완충띠 포함)`);
  for (let i = 0; i < rs.length; i++) for (let j = i + 1; j < rs.length; j++) {
    const a = rs[i], b = rs[j], gx = Math.max(b.x - (a.x + a.w), a.x - (b.x + b.w)), gy = Math.max(b.y - (a.y + a.h), a.y - (b.y + b.h));
    if (Math.max(gx, gy) < layout.buf) bad.push(`${a.name} ↔ ${b.name}: 간격 ${Math.max(gx, gy)} px`);
  }
  return bad;
}

// ---- 바탕 부채꼴 ----
const CX = W / 2, CY = H / 2;
// 각도는 타원 정규 좌표 (u, v) = (dx / (W/2), dy / (H/2))에서 잰다. edgeDist는 부채꼴 경계까지의 거리를 짧은 변 기준으로 낮게 어림한 px 값.
// 큰 반복문에서 객체를 만들지 않으려고 out에 쓴다
export function sectorOf(x, y, out = { level: 0, edgeDist: 0 }) {
  const u = (x + 0.5 - CX) / (W / 2), v = (y + 0.5 - CY) / (H / 2), t = (Math.atan2(v, u) + Math.PI) / ((2 * Math.PI) / SECTORS);
  const s = Math.min(SECTORS - 1, Math.floor(t));
  out.edgeDist = Math.min(t - s, s + 1 - t) * ((2 * Math.PI) / SECTORS) * Math.hypot(u, v) * (Math.min(W, H) / 2);
  out.level = s % BG_L.length;
  return out;
}

// 영역 + 완충띠 표식: 0 = 바탕, 1 = 완충띠·영역
let regionMaskCache = null;
export function regionMask() {
  if (regionMaskCache) return regionMaskCache;
  const m = new Uint8Array(W * H);
  for (const r of LAYOUT.regions) {
    for (let y = Math.max(0, r.y - BUF); y < Math.min(H, r.y + r.h + BUF); y++) m.fill(1, y * W + Math.max(0, r.x - BUF), y * W + Math.min(W, r.x + r.w + BUF));
  }
  return (regionMaskCache = m);
}

// ---- 합성 견본(입력) ----
// 마커: square px 체커, 왼쪽 위 칸이 검정
const markerBit = (r, x, y) => ((Math.floor(x / r.square) + Math.floor(y / r.square)) & 1) === 1;
const labL = (r, g, b) => { const t = new Float64Array(3); rgbToLab(r, g, b, t, 0); return t[0]; };

function renderProbe() {
  const data = new Uint8ClampedArray(W * H * 4);
  const mask = regionMask(), bgV = BG_L.map(grayOfL), bufV = grayOfL(BUF_L), sec = { level: 0, edgeDist: 0 };
  for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) {
    const i = y * W + x, v = mask[i] ? bufV : bgV[sectorOf(x, y, sec).level];
    data[i * 4] = data[i * 4 + 1] = data[i * 4 + 2] = v; data[i * 4 + 3] = 255;
  }
  const put = (x, y, r, g, b) => { const p = (y * W + x) * 4; data[p] = r; data[p + 1] = g; data[p + 2] = b; };
  const fill = (x0, y0, w, h, r, g = r, b = r) => { for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) put(x, y, r, g, b); };
  for (const r of LAYOUT.regions) {
    if (r.kind === 'core') {
      for (let cy = 0; cy < CORE_ROWS; cy++) for (let cx = 0; cx < CORE_COLS; cx++) {
        const c = LAYOUT.coreGrid[cy * CORE_COLS + cx];
        if (c < 0) fill(r.x + cx * CELL, r.y + cy * CELL, CELL, CELL, REF_GRAY);
        else fill(r.x + cx * CELL, r.y + cy * CELL, CELL, CELL, gridValue(c % N), gridValue(Math.floor(c / N) % N), gridValue(Math.floor(c / (N * N))));
      }
    } else if (r.kind === 'marker') {
      for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) { const v = markerBit(r, x, y) ? 255 : 0; put(r.x + x, r.y + y, v, v, v); }
    } else if (r.kind === 'tonePanel') { for (const p of r.steps) fill(p.x, p.y, p.w, p.h, grayOfL(p.L)); }
    else if (r.kind === 'flat') fill(r.x, r.y, r.w, r.h, grayOfL(r.L));
    else if (r.kind === 'freqPanel') {
      for (const p of r.patches) {
        const m = grayOfL(p.L);
        for (let x = 0; x < p.w; x++) { const v = Math.round(m + p.amp * Math.sin((2 * Math.PI * x) / p.period)); for (let y = 0; y < p.h; y++) put(p.x + x, p.y + y, v, v, v); }
      }
    } else if (r.kind === 'edge') {
      const [lo, hi] = r.Ls.map(grayOfL), c = Math.cos((r.deg * Math.PI) / 180), s = Math.sin((r.deg * Math.PI) / 180);
      for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) {
        const d = (x + 0.5 - r.w / 2) * c - (y + 0.5 - r.h / 2) * s; // 에지까지 부호 있는 거리. 픽셀 너비 안에서는 면적 비율로 섞는다
        const v = Math.round(lo + (hi - lo) * Math.min(1, Math.max(0, d + 0.5)));
        put(r.x + x, r.y + y, v, v, v);
      }
    } else if (r.kind === 'locality') {
      fill(r.x, r.y, r.w, r.h, grayOfL(r.surroundL));
      const o = (r.w - LOC_CENTER) / 2;
      fill(r.x + o, r.y + o, LOC_CENTER, LOC_CENTER, grayOfL(r.L));
    } else if (r.kind === 'dehaze') {
      fill(r.x, r.y, r.w / 2, r.h, ...r.rgb);
      fill(r.x + r.w / 2, r.y, r.w / 2, r.h, grayOfL(labL(...r.rgb)));
    }
  }
  return data;
}

// 편집이 걸리기 전 견본. 읽기 중 여러 번 쓰므로 한 번만 만들고, 읽기가 끝나면 clearProbeCaches로 놓는다
let inputCache = null;
export const inputProbe = () => (inputCache ??= renderProbe());
export function clearProbeCaches() {
  inputCache = null;
  regionMaskCache = null;
}

// ---- 마커 정합 ----
export const INVALID_PROBE_MESSAGE = '받은 견본 이미지를 그대로 사용해 주세요.';

// 마커는 ±search px 안에서 체커와의 정규 상관이 가장 큰 위치를 찾는다. 크기가 다르거나 maxShift보다 어긋나면 작가 파일 문제(INVALID_PROBE)다.
// 어긋남이 있지만 허용 범위 안이거나 상관이 낮으면 경고만 모아 돌려준다
export function registerMarkers(rgba, width, height, { search = 12, maxShift = 2 } = {}) {
  if (width !== W || height !== H || rgba.length !== W * H * 4) throw ExtractionFailure.invalidProbe(INVALID_PROBE_MESSAGE);
  const markers = MARKERS.map((r) => {
    let best = { dx: 0, dy: 0, score: -Infinity };
    for (let dy = -search; dy <= search; dy++) for (let dx = -search; dx <= search; dx++) {
      let sxy = 0, sx = 0, sxx = 0, n = 0;
      for (let y = 0; y < r.h; y++) {
        const yy = r.y + y + dy;
        if (yy < 0 || yy >= H) continue;
        for (let x = 0; x < r.w; x++) {
          const xx = r.x + x + dx;
          if (xx < 0 || xx >= W) continue;
          const p = (yy * W + xx) * 4, v = rgba[p] + rgba[p + 1] + rgba[p + 2], t = markerBit(r, x, y) ? 1 : -1;
          sxy += v * t; sx += v; sxx += v * v; n++;
        }
      }
      const sd = Math.sqrt(Math.max(1e-9, sxx / n - (sx / n) ** 2)), score = sxy / n / sd; // t는 평균 0·분산 1이라 이것이 상관 계수
      if (score > best.score) best = { dx, dy, score };
    }
    return { name: r.name, ...best };
  });
  const warnings = [];
  for (const m of markers) {
    if (Math.abs(m.dx) > maxShift || Math.abs(m.dy) > maxShift) throw ExtractionFailure.invalidProbe(INVALID_PROBE_MESSAGE);
    if (m.dx || m.dy) warnings.push(`${m.name} 마커가 (${m.dx}, ${m.dy}) px 어긋나 있어요.`);
    if (m.score < 0.5) warnings.push(`${m.name} 마커 상관이 낮아요(${m.score.toFixed(2)}).`);
  }
  return { markers, warnings };
}
