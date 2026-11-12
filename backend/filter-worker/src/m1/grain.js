// 그레인 읽기: 평탄 패치 8개 → 연산자 파라미터(진폭 8점·크기·거칠기).
// 목표는 픽셀 일치가 아니라 통계 일치다: 휘도 구간별 σ와 방사 파워 스펙트럼.
//
//   grain = { amp, size, edge, tilt, seed }   (이미지 크기와 무관한 값만 가진다)
//     amp   : 입력 L* 매듭(GRAIN_L_KNOTS 8점) 위의 잡음 표준편차(L* 단위). 매듭 사이는 선형, 양끝 바깥은 끝 값을 유지한다.
//     size  : 입자 반경 σ = size × 짧은 변(px). 파워 스펙트럼 꺾임 주파수 fc = 1/(2πσ) 주기/px.
//     edge  : 꺾임의 가파름 p. 2면 가우시안, 클수록 fc까지 평탄하다가 급락한다.
//     tilt  : 저주파 감쇠 지수 t ≥ 0. 0이면 백색 바탕, 클수록 고주파 쪽으로 기운 푸른 잡음.
//     seed  : 정수 시드. 같은 시드·같은 좌표면 어느 플랫폼에서나 같은 잡음이다.
//   잡음장 n = g ⊛ W: W는 좌표 해시로 만든 단위 분산 백색 잡음(정수 연산만), g는 H(f) = (f/fc)^(t/2) · exp(−½ (f/fc)^p)의 역변환 FIR(Σg² = 1).
//   적용: L*를 입력 값 L로 읽어 L_out = L + amp(L)·n. a·b는 그대로 두고 sRGB로 되돌린다. amp가 모두 0이면 입력을 그대로 복사한다.
//
// 읽기는 통계 정의를 따른다: 고주파 hp = L* − gaussianBlur(L*, 1px). 견본 평탄 패치는 입력이 상수 회색이라 기준 고주파가 0이므로
// 패치 가운데 192×192의 hp 표준편차가 곧 그레인의 순수 σ다(블러는 256×256 전체에서 하고 가장자리 32 px를 버린다).
// 무편집 견본(합성 입력)의 hp σ는 0이라 잡음 바닥을 빼지 않는다.
import { gaussianBlur, labPlanes, labToRgba, rgbToLab } from './color.js';
import { FLATS, H, W, grayOfL } from './layout.js';
import { mean } from './numeric.js';

export const GRAIN_L_KNOTS = [5, 20, 35, 50, 65, 80, 90, 97]; // 견본 평탄 패치 L*와 같다
const SIGMA_HP = 1; // 고주파 = x − blur(x, 1px)

// ---------------------------------------------------------------- 1. 연산자 (잡음장, 적용)
function hash01(x, y, seed) {
  let h = Math.imul(x | 0, 0x27d4eb2d) ^ Math.imul(y | 0, 0x165667b1) ^ Math.imul(seed | 0, 0x9e3779b1);
  h ^= h >>> 15; h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13; h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}
// 균등 4개의 합(Irwin-Hall)을 평균 0·분산 1로 맞춘다: (Σu − 2)·√3. 근사 가우시안이고 덧셈·곱셈만 쓴다
const SQRT3 = Math.fround(Math.sqrt(3));
export function whiteAt(x, y, seed) {
  const s = Math.imul(seed | 0, 4);
  return (hash01(x, y, s) + hash01(x, y, s + 1) + hash01(x, y, s + 2) + hash01(x, y, s + 3) - 2) * SQRT3;
}

const KERNEL_RMAX = 24;
// 1D FIR, Σg² = 1. σ가 0.2px 미만이고 tilt가 0이면 델타 (Nyquist까지 평탄한 백색)
export function kernel1D(sigma, edge, tilt) {
  if (!(sigma >= 0.2) && !(tilt > 0)) return Float64Array.of(1);
  const NF = 1024, fc = 1 / (2 * Math.PI * Math.max(sigma, 0.05)), half = [];
  for (let n = 0; n <= KERNEL_RMAX; n++) {
    let s = 0;
    for (let i = 0; i < NF; i++) { const f = ((i + 0.5) / NF) * 0.5, x = f / fc; s += Math.pow(x, tilt / 2) * Math.exp(-0.5 * Math.pow(x, edge)) * Math.cos(2 * Math.PI * f * n); }
    half.push((2 * s * 0.5) / NF);
  }
  let R = KERNEL_RMAX;
  while (R > 1 && Math.abs(half[R]) < 1e-3 * Math.max(...half.map(Math.abs))) R--;
  const g = new Float64Array(2 * R + 1);
  let e = 0;
  for (let n = -R; n <= R; n++) { g[n + R] = half[Math.abs(n)]; e += g[n + R] * g[n + R]; }
  const c = 1 / Math.sqrt(e);
  return g.map((v) => v * c);
}
const grainKernel = (grain, w, h) => kernel1D(grain.size * Math.min(w, h), grain.edge, grain.tilt);

// 단위 분산 잡음장, 길이 w·h (전역 좌표 (x0, y0)부터). refW·refH: 크기 비율의 기준 프레임
export function grainField(w, h, grain, o = {}) {
  const x0 = o.x0 || 0, y0 = o.y0 || 0, g = grainKernel(grain, o.refW || w, o.refH || h);
  const R = (g.length - 1) / 2, pw = w + 2 * R, ph = h + 2 * R, seed = (grain.seed | 0) * 16 + 1;
  const white = new Float32Array(pw * ph);
  for (let y = 0; y < ph; y++) for (let x = 0; x < pw; x++) white[y * pw + x] = whiteAt(x + x0 - R, y + y0 - R, seed);
  const tmp = new Float32Array(w * ph), out = new Float32Array(w * h);
  for (let y = 0; y < ph; y++) for (let x = 0; x < w; x++) { let s = 0; for (let t = 0; t < g.length; t++) s += g[t] * white[y * pw + x + t]; tmp[y * w + x] = s; }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) { let s = 0; for (let t = 0; t < g.length; t++) s += g[t] * tmp[(y + t) * w + x]; out[y * w + x] = s; }
  return out;
}

// 입력 L*에서의 진폭 (매듭 사이 선형, 바깥은 끝 값)
function ampAt(amp, L) {
  const K = GRAIN_L_KNOTS, n = K.length;
  if (L <= K[0]) return amp[0];
  if (L >= K[n - 1]) return amp[n - 1];
  let k = 0;
  while (L > K[k + 1]) k++;
  return amp[k] + ((amp[k + 1] - amp[k]) * (L - K[k])) / (K[k + 1] - K[k]);
}

// rgba(w×h, 앞 단계 출력)에 그레인을 얹는다. o.x0·o.y0: 잘라낸 영역의 전역 좌표, o.refW·o.refH: 크기 비율의 기준 프레임
export function applyGrain(rgba, w, h, grain, dst, o = {}) {
  const n = w * h;
  if (!grain.amp.some((v) => v !== 0)) { dst.set(rgba.subarray ? rgba.subarray(0, n * 4) : rgba); return dst; }
  const lab = labPlanes(rgba, n), noise = grainField(w, h, grain, o), dL = new Float32Array(n);
  for (let i = 0; i < n; i++) dL[i] = ampAt(grain.amp, lab.L[i]) * noise[i];
  return labToRgba(lab, n, dL, dst);
}

// ---------------------------------------------------------------- 2. 스펙트럼
// 방사 파워 스펙트럼: 64×64 블록(Hann 창)의 2D FFT 파워를 정수 반경 고리 k = 2..32(주기 32 px ~ 나이키스트)로 평균하고 8개 구간(SPEC_EDGES)으로 묶는다.
// 비교는 모양만 본다(구간 평균으로 나눠 정규화). 지표 = ln 파워의 구간 RMS 차.
const NB = 64; // FFT 블록 한 변
const SPEC_EDGES = [2, 3, 4, 6, 8, 12, 16, 24, 33]; // 고리 k 구간 [e_i, e_{i+1})
const NS = SPEC_EDGES.length - 1;
// 정규화된(구간 평균 1) 파워의 하한. 8비트 양자화·JPEG 바닥 아래의 구간은 신호가 아니라 바닥이라 ln 차를 하한에서 자른다
const SPEC_FLOOR = 5e-3;

const TW = (() => { const re = new Float64Array(NB / 2), im = new Float64Array(NB / 2); for (let k = 0; k < NB / 2; k++) { re[k] = Math.cos((-2 * Math.PI * k) / NB); im[k] = Math.sin((-2 * Math.PI * k) / NB); } return { re, im }; })();
function fft64(re, im) {
  const n = NB;
  for (let i = 1, j = 0; i < n; i++) {
    let b = n >> 1;
    for (; j & b; b >>= 1) j ^= b;
    j ^= b;
    if (i < j) { let t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
  }
  for (let len = 2; len <= n; len <<= 1) {
    const step = n / len;
    for (let i = 0; i < n; i += len) for (let k = 0; k < len / 2; k++) {
      const c = TW.re[k * step], s = TW.im[k * step], a = i + k, b = a + len / 2;
      const vr = re[b] * c - im[b] * s, vi = re[b] * s + im[b] * c;
      re[b] = re[a] - vr; im[b] = im[a] - vi; re[a] += vr; im[a] += vi;
    }
  }
}
const HANN = Float64Array.from({ length: NB }, (_, i) => 0.5 * (1 - Math.cos((2 * Math.PI * (i + 0.5)) / NB)));
// 고리 인덱스 표 (u, v) → k, 범위 밖은 −1
const RING_OF = Int16Array.from({ length: NB * NB }, (_, idx) => {
  const u = idx % NB, v = Math.floor(idx / NB), ku = u > NB / 2 ? u - NB : u, kv = v > NB / 2 ? v - NB : v;
  const k = Math.round(Math.hypot(ku, kv));
  return k >= SPEC_EDGES[0] && k < SPEC_EDGES[NS] ? k : -1;
});
const BIN_OF_RING = Int8Array.from({ length: NB / 2 + 1 }, (_, k) => SPEC_EDGES.findIndex((e, i) => i < NS && k >= e && k < SPEC_EDGES[i + 1]));

// 평면 L의 (x0, y0)에서 시작하는 64×64 블록의 8구간 평균 파워. 구간 안 고리 평균 → 구간 안 고리 단순 평균
const SCR = { re: new Float64Array(NB * NB), im: new Float64Array(NB * NB), a: new Float64Array(NB), b: new Float64Array(NB) };
function blockSpec(L, w, x0, y0) {
  const { re, im, a, b } = SCR;
  let m = 0;
  for (let y = 0; y < NB; y++) for (let x = 0; x < NB; x++) m += L[(y0 + y) * w + x0 + x];
  m /= NB * NB;
  for (let y = 0; y < NB; y++) for (let x = 0; x < NB; x++) { re[y * NB + x] = (L[(y0 + y) * w + x0 + x] - m) * HANN[x] * HANN[y]; im[y * NB + x] = 0; }
  for (let y = 0; y < NB; y++) { for (let x = 0; x < NB; x++) { a[x] = re[y * NB + x]; b[x] = im[y * NB + x]; } fft64(a, b); for (let x = 0; x < NB; x++) { re[y * NB + x] = a[x]; im[y * NB + x] = b[x]; } }
  for (let x = 0; x < NB; x++) { for (let y = 0; y < NB; y++) { a[y] = re[y * NB + x]; b[y] = im[y * NB + x]; } fft64(a, b); for (let y = 0; y < NB; y++) { re[y * NB + x] = a[y]; im[y * NB + x] = b[y]; } }
  const ring = new Float64Array(NB / 2 + 1), cnt = new Float64Array(NB / 2 + 1);
  for (let i = 0; i < NB * NB; i++) { const k = RING_OF[i]; if (k < 0) continue; ring[k] += re[i] * re[i] + im[i] * im[i]; cnt[k]++; }
  const out = new Float64Array(NS), c = new Float64Array(NS);
  for (let k = SPEC_EDGES[0]; k < SPEC_EDGES[NS]; k++) { const s = BIN_OF_RING[k]; out[s] += ring[k] / cnt[k]; c[s]++; }
  return Array.from(out, (v, s) => v / c[s]);
}
const addSpec = (acc, s) => { for (let i = 0; i < NS; i++) acc[i] += s[i]; };
// 구간 평균으로 나눈 모양
const shapeOf = (s) => { const m = mean(Array.from(s)); return Array.from(s, (v) => v / m); };
// 모양 둘의 ln 파워 구간 RMS 차. floor: 정규화된 파워의 하한 (SPEC_FLOOR)
export function specRms(a, b, floor = SPEC_FLOOR) {
  const fa = shapeOf(a), fb = shapeOf(b);
  return Math.sqrt(mean(fa.map((v, i) => (Math.log(Math.max(v, floor)) - Math.log(Math.max(fb[i], floor))) ** 2)));
}

// ---------------------------------------------------------------- 3. 평탄 패치 측정
// 256×256 L* 평면 → { meanL, hpStd, rawStd, spec } (가운데 192×192, 스펙트럼은 그 안의 겹친 5×5 블록 평균)
export function patchStats(L, w = 256, h = 256) {
  const bl = gaussianBlur(L, w, h, SIGMA_HP), hf = [], lv = [];
  for (let y = 32; y < h - 32; y++) for (let x = 32; x < w - 32; x++) { const i = y * w + x; hf.push(L[i] - bl[i]); lv.push(L[i]); }
  const m = mean(lv), hm = mean(hf);
  const spec = new Array(NS).fill(0);
  // 가운데 192×192 안에서 절반씩 겹치는 64×64 블록 5×5개 (겹침 때문에 독립 표본은 25개보다 적다)
  for (let by = 0; by < 5; by++) for (let bx = 0; bx < 5; bx++) addSpec(spec, blockSpec(L, w, 32 + bx * 32, 32 + by * 32));
  return { meanL: m, hpStd: Math.sqrt(mean(hf.map((v) => (v - hm) ** 2))), rawStd: Math.sqrt(mean(lv.map((v) => (v - m) ** 2))), spec: spec.map((v) => v / 25) };
}

// 견본(RGBA, 전체 크기) → 평탄 패치 8개의 통계
export function measureFlats(rgba, width = W) {
  return FLATS.map((r) => {
    const L = new Float32Array(r.w * r.h), t = new Float64Array(3);
    for (let y = 0; y < r.h; y++) for (let x = 0; x < r.w; x++) { const p = ((r.y + y) * width + r.x + x) * 4; rgbToLab(rgba[p], rgba[p + 1], rgba[p + 2], t, 0); L[y * r.w + x] = t[0]; }
    return { target: r.L, ...patchStats(L, r.w, r.h) };
  });
}

// ---------------------------------------------------------------- 4. 모델 스펙트럼과 (크기, 가파름, 기울기) 맞춤
// 연산자의 FIR 커널로부터 정확한 이산 주파수 응답을 만든다: |H(f)|² (f = k/NB, k = 0..NB/2), 2D는 축별 곱
export function modelShape(sigma0px, edge, tilt) {
  const g = kernel1D(sigma0px, edge, tilt), R = (g.length - 1) / 2;
  const H2 = Float64Array.from({ length: NB / 2 + 1 }, (_, k) => { let s = 0; for (let n = -R; n <= R; n++) s += g[n + R] * Math.cos((2 * Math.PI * k * n) / NB); return s * s; });
  const ring = new Float64Array(NB / 2 + 1), cnt = new Float64Array(NB / 2 + 1);
  for (let idx = 0; idx < NB * NB; idx++) {
    const k = RING_OF[idx]; if (k < 0) continue;
    const u = idx % NB, v = Math.floor(idx / NB), au = u > NB / 2 ? NB - u : u, av = v > NB / 2 ? NB - v : v;
    ring[k] += H2[au] * H2[av]; cnt[k]++;
  }
  const out = new Float64Array(NS), c = new Float64Array(NS);
  for (let k = SPEC_EDGES[0]; k < SPEC_EDGES[NS]; k++) { const s = BIN_OF_RING[k]; out[s] += ring[k] / cnt[k]; c[s]++; }
  return Array.from(out, (v, s) => v / c[s]);
}

// 측정된 스펙트럼 모양(8구간)에 모델(σ, edge, tilt)을 맞춘다. 거친 격자 탐색 후 좌표별로 좁혀 가며 다시 탐색
export function fitShape(spec) {
  const sg = (lo, hi, n) => Array.from({ length: n }, (_, i) => lo * (hi / lo) ** (i / (n - 1)));
  const ln = (lo, hi, n) => Array.from({ length: n }, (_, i) => lo + ((hi - lo) * i) / (n - 1));
  let best = { rms: Infinity };
  const tryP = (sigma0px, edge, tilt) => {
    if (!(edge >= 1.2 && edge <= 10 && tilt >= 0 && tilt <= 8 && sigma0px >= 0.05 && sigma0px <= 12)) return;
    const e = specRms(modelShape(sigma0px, edge, tilt), spec);
    if (e < best.rms) best = { sigma0px, edge, tilt, rms: e };
  };
  for (const s of sg(0.1, 8, 24)) for (const p of [1.5, 2, 2.5, 3, 4, 6, 8]) for (const t of [0, 0.5, 1, 2, 3, 4, 6]) tryP(s, p, t);
  for (const f of [0.3, 0.15, 0.07, 0.03]) {
    for (let pass = 0; pass < 2; pass++) {
      const c = best;
      for (const s of ln(1 - f, 1 + f, 5)) tryP(c.sigma0px * s, c.edge, c.tilt);
      const c2 = best;
      for (const e of ln(1 - f, 1 + f, 5)) tryP(c2.sigma0px, c2.edge * e, c2.tilt);
      const c3 = best;
      for (const t of ln(-f * 3, f * 3, 7)) tryP(c3.sigma0px, c3.edge, c3.tilt + t);
    }
  }
  return best;
}

// ---------------------------------------------------------------- 5. 진폭: 연산자를 평탄 패치에 걸어 정답 hp σ에 맞춘다
const patchRgba = (L) => { const v = grayOfL(L), a = new Uint8ClampedArray(256 * 256 * 4); for (let i = 0; i < 256 * 256; i++) { a[i * 4] = a[i * 4 + 1] = a[i * 4 + 2] = v; a[i * 4 + 3] = 255; } return a; };
function patchPlane(rgba) { const L = new Float32Array(256 * 256), t = new Float64Array(3); for (let i = 0; i < L.length; i++) { rgbToLab(rgba[i * 4], rgba[i * 4 + 1], rgba[i * 4 + 2], t, 0); L[i] = t[0]; } return L; }
// 연산자를 L* = L인 회색 패치 한 장에 걸어 통계를 낸다 (amp는 그 L에서의 값 하나). 크기 비율은 견본 프레임(3072×4096) 기준
function synthPatch(L, a, shape, seed = 7) {
  const grain = { amp: new Array(GRAIN_L_KNOTS.length).fill(a), size: shape.sigma0px / W, edge: shape.edge, tilt: shape.tilt, seed };
  const out = applyGrain(patchRgba(L), 256, 256, grain, new Uint8ClampedArray(256 * 256 * 4), { refW: W, refH: H });
  return patchStats(patchPlane(out));
}
// 해석적 맞춤은 8비트 양자화 바닥과 JPEG 잡음을 모른다. 연산자를 평탄 패치에 실제로 걸어 본 스펙트럼이 측정 스펙트럼에 가까워지게
// (σ, edge, tilt)를 좌표별로 다듬는다. 목표 패치 몇 개의 (L*, 측정 raw σ, 측정 스펙트럼)
function refineBySynth(init, targets) {
  const obj = (sh) => Math.sqrt(mean(targets.map((t) => specRms(synthPatch(t.L, t.raw, sh).spec, t.spec) ** 2)));
  let best = { ...init, rms: obj(init) };
  const ok = (sh) => sh.edge >= 1.2 && sh.edge <= 10 && sh.tilt >= 0 && sh.tilt <= 8 && sh.sigma0px >= 0.05 && sh.sigma0px <= 12;
  for (const f of [0.3, 0.15, 0.07, 0.03]) for (let pass = 0; pass < 2; pass++) for (const key of ['sigma0px', 'edge', 'tilt']) for (const sgn of [-1, 1]) {
    const cand = { ...best, [key]: key === 'tilt' ? best[key] + sgn * f * 3 : best[key] * (1 + sgn * f) };
    if (!ok(cand)) continue;
    const r = obj(cand);
    if (r < best.rms) best = { ...cand, rms: r };
  }
  return best;
}
const AMP_FLOOR = 0.01; // 이보다 작은 hp σ는 잡음이 아니라 양자화 바닥이라 진폭을 0으로 둔다
function fitAmps(targetHp, shape) {
  return targetHp.map((t, k) => {
    if (!(t > AMP_FLOOR)) return 0;
    let a = t * 1.4;
    for (let it = 0; it < 6; it++) { const m = synthPatch(GRAIN_L_KNOTS[k], a, shape).hpStd; if (!(m > 0)) { a *= 2; continue; } a *= t / m; }
    return a;
  });
}

// 평탄 패치 측정 → 그레인 파라미터. 모양은 클리핑이 없는 가운데 L* 20~90 패치의 모양 평균(5와 97은 클리핑·양자화가 모양을 바꾼다),
// 진폭은 순수 σ를 패치 실제 L*에 놓은 점들을 매듭 L*에 선형 보간한 값이다
export function grainFromFlats(flats) {
  const pure = flats.map((f) => f.hpStd);
  // 어느 패치에도 잡음이 없으면(진폭이 모두 0이 되는 경우) 스펙트럼 모양이 정해지지 않으므로 NaN 대신 항등 그레인을 돌려준다
  if (!pure.some((v) => v > AMP_FLOOR)) return { amp: GRAIN_L_KNOTS.map(() => 0), size: 0, edge: 4, tilt: 0, sigma0px: 0, shapeRms: null };
  const mid = flats.filter((f) => f.target >= 20 && f.target <= 90).map((f) => shapeOf(f.spec));
  const avg = Array.from({ length: NS }, (_, i) => mean(mid.map((s) => s[i])));
  const shape = refineBySynth(fitShape(avg), flats.filter((f) => [35, 50, 65].includes(f.target)).map((f) => ({ L: f.target, raw: f.rawStd, spec: f.spec })));
  const ampAtTarget = fitAmps(pure, shape);
  const pts = flats.map((f, k) => [f.meanL, ampAtTarget[k]]).sort((a, b) => a[0] - b[0]);
  const at = (L) => {
    if (L <= pts[0][0]) return pts[0][1];
    if (L >= pts[pts.length - 1][0]) return pts[pts.length - 1][1];
    let k = 0;
    while (L > pts[k + 1][0]) k++;
    return pts[k][1] + ((pts[k + 1][1] - pts[k][1]) * (L - pts[k][0])) / (pts[k + 1][0] - pts[k][0]);
  };
  return { amp: GRAIN_L_KNOTS.map(at), size: shape.sigma0px / W, edge: shape.edge, tilt: shape.tilt, sigma0px: shape.sigma0px, shapeRms: shape.rms };
}
