// 레시피 JSON(schemaVersion 1)의 인코딩·디코딩과 서버 기준 렌더러(applyRecipe).
// 앱이 맞춰야 하는 적용 순서(그레인은 분기와 무관하게 항상 마지막):
//   M1 = LUT → 지역 톤 → 디테일 → 비네팅 → 그레인
//   A2 = LUT → A2 격자(흐림 평면은 원본 입력에서) → 그레인
//   G1 = M1 전체(그레인 없이) → G1 격자(흐림 평면은 원본 입력에서) → 그레인
import { readFileSync } from 'node:fs';
import { GRID_KINDS } from './grid/grid-core.js';
import { applyA2, applyG1 } from './grid/apply.js';
import { applyM1 } from './m1/apply.js';
import { GRAIN_L_KNOTS, applyGrain } from './m1/grain.js';

export const SCHEMA_VERSION = 1;
export const PROBE_VERSION = 2;
export const EXTRACTOR_VERSION = JSON.parse(readFileSync(new URL('../package.json', import.meta.url), 'utf8')).version;

const LUT_SIZE = 33;
const LUT_BYTES = LUT_SIZE ** 3 * 3;
const BRANCHES = ['M1', 'A2', 'G1'];

const toBase64 = (bytes) => Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength).toString('base64');
const fromBase64 = (text) => { const b = Buffer.from(text, 'base64'); return new Uint8Array(b.buffer, b.byteOffset, b.length); };

// 격자 θ: f64 → f32 little-endian 바이트 (순서는 grid-core의 solve 주석과 같다)
function encodeTheta(theta) {
  const bytes = Buffer.alloc(theta.length * 4);
  theta.forEach((v, i) => bytes.writeFloatLE(v, i * 4));
  return bytes.toString('base64');
}
const array = (typed) => Array.from(typed);

/**
 * @param {{ branch: 'M1'|'A2'|'G1', m1: object, grid?: { dims: number[], theta: Float64Array }|null }} input m1: extractM1의 결과, grid: A2·G1일 때 격자
 * @returns {object} JSON.stringify할 수 있는 레시피
 */
export function encodeRecipe({ branch, m1, grid = null }) {
  const ops = { lut: { size: LUT_SIZE, encoding: 'rgb8', data: toBase64(m1.lut) } };
  if (branch !== 'A2') { // A2는 LUT 뒤에 격자만 건다. 비네팅·지역 톤·디테일은 격자가 흡수한다
    if (m1.V) ops.vignette = { ...m1.V, T: array(m1.V.T) };
    if (m1.C) ops.localTone = { ...m1.C, T: array(m1.C.T) };
    if (m1.K) ops.detail = { sigmas: m1.K.sigmas, eKnots: m1.K.eKnots, mKnots: m1.K.mKnots, K: array(m1.K.K) };
  }
  if (branch !== 'M1') ops.grid = { kind: branch, dims: grid.dims, encoding: 'f32le', data: encodeTheta(grid.theta) };
  if (m1.grain) ops.grain = { amp: array(m1.grain.amp), size: m1.grain.size, edge: m1.grain.edge, tilt: m1.grain.tilt, seed: m1.grain.seed ?? 1 };
  return { schemaVersion: SCHEMA_VERSION, extractorVersion: EXTRACTOR_VERSION, probeVersion: PROBE_VERSION, branch, colorSpace: 'sRGB', ops };
}

// 레시피 형식 오류. 서버와 앱 계약 검사에 쓰며 ExtractionFailure가 아니다(작가 파일 문제가 아니라 저장된 레시피가 깨진 것)
export class RecipeFormatError extends Error {
  constructor(detail) {
    super(`레시피 형식 오류: ${detail}`);
    this.name = 'RecipeFormatError';
  }
}

const bad = (detail) => { throw new RecipeFormatError(detail); };
const isObject = (v) => typeof v === 'object' && v !== null && !Array.isArray(v);
const finite = (v, name) => (Number.isFinite(v) ? v : bad(`${name}가 유한한 숫자가 아니에요.`));
const integer = (v, name, min = 1) => (Number.isInteger(v) && v >= min ? v : bad(`${name}가 ${min} 이상의 정수가 아니에요.`));
function numbers(v, name, length = null) {
  if (!Array.isArray(v)) bad(`${name}가 배열이 아니에요.`);
  if (length !== null && v.length !== length) bad(`${name} 길이가 ${length}이어야 하는데 ${v.length}이에요.`);
  v.forEach((x, i) => finite(x, `${name}[${i}]`));
  return v;
}
const object = (v, name) => (isObject(v) ? v : bad(`${name}가 객체가 아니에요.`));

function decodeVignette(v) {
  object(v, 'vignette');
  const nr = integer(v.nr, 'vignette.nr', 2), nv = integer(v.nv, 'vignette.nv', 2);
  if (v.mode !== 'dark' && v.mode !== 'light') bad('vignette.mode가 dark나 light가 아니에요.');
  if (v.shape !== undefined) { object(v.shape, 'vignette.shape'); finite(v.shape.p, 'vignette.shape.p'); finite(v.shape.c, 'vignette.shape.c'); }
  return { ...v, vKnots: numbers(v.vKnots, 'vignette.vKnots', nv), T: Float64Array.from(numbers(v.T, 'vignette.T', nr * nv)) };
}

function decodeTone(t) {
  object(t, 'localTone');
  const ny = integer(t.ny, 'localTone.ny', 2), nb = integer(t.nb, 'localTone.nb');
  for (const key of ['yLo', 'yHi', 'bLo', 'bHi', 'sigmaPct']) finite(t[key], `localTone.${key}`);
  return { ...t, T: Float64Array.from(numbers(t.T, 'localTone.T', ny * nb)) };
}

function decodeDetail(d) {
  object(d, 'detail');
  const sigmas = numbers(d.sigmas, 'detail.sigmas'), eKnots = numbers(d.eKnots, 'detail.eKnots'), mKnots = numbers(d.mKnots, 'detail.mKnots');
  if (sigmas.length < 2 || eKnots.length < 1 || mKnots.length < 1) bad('detail 매듭이 비어 있어요.');
  return { sigmas, eKnots, mKnots, K: Float64Array.from(numbers(d.K, 'detail.K', (sigmas.length - 1) * eKnots.length * mKnots.length)) };
}

function decodeGrain(g) {
  object(g, 'grain');
  for (const key of ['size', 'edge', 'tilt']) finite(g[key], `grain.${key}`);
  integer(g.seed, 'grain.seed', 0);
  return { amp: [...numbers(g.amp, 'grain.amp', GRAIN_L_KNOTS.length)], size: g.size, edge: g.edge, tilt: g.tilt, seed: g.seed };
}

/**
 * 레시피 JSON을 applyM1·applyA2·applyG1이 받는 구조로 되돌린다. 모양·길이·유한성을 엄격히 검사하고 어긋나면 RecipeFormatError.
 * @returns {{ branch: string, m1: { lut: Uint8Array, V, C, K, grain }, grid: { kind: string, dims: number[], theta: Float64Array }|null }}
 */
export function decodeRecipe(json) {
  if (json?.schemaVersion !== SCHEMA_VERSION) bad(`지원하지 않는 버전이에요: ${json?.schemaVersion}`);
  const { branch, ops } = json;
  if (!BRANCHES.includes(branch)) bad(`알 수 없는 분기예요: ${branch}`);
  object(ops, 'ops');
  if (ops.lut?.size !== LUT_SIZE || ops.lut.encoding !== 'rgb8' || typeof ops.lut.data !== 'string') bad('LUT 형식이 맞지 않아요.');
  const lut = fromBase64(ops.lut.data);
  if (lut.length !== LUT_BYTES) bad(`LUT 길이가 ${LUT_BYTES}바이트여야 하는데 ${lut.length}바이트예요.`);
  const m1 = {
    lut,
    V: ops.vignette === undefined ? null : decodeVignette(ops.vignette),
    C: ops.localTone === undefined ? null : decodeTone(ops.localTone),
    K: ops.detail === undefined ? null : decodeDetail(ops.detail),
    grain: ops.grain === undefined ? null : decodeGrain(ops.grain),
  };
  let grid = null;
  if (branch !== 'M1') {
    const g = object(ops.grid, 'grid');
    if (g.kind !== branch || g.encoding !== 'f32le' || typeof g.data !== 'string') bad('격자 형식이 맞지 않아요.');
    const expected = GRID_KINDS[branch].dims;
    if (!Array.isArray(g.dims) || g.dims.length !== expected.length || g.dims.some((d, i) => d !== expected[i])) bad(`격자 꼴이 [${expected}]이어야 해요.`);
    const length = expected.reduce((a, b) => a * b, 1), bytes = Buffer.from(g.data, 'base64');
    if (bytes.length !== length * 4) bad(`격자 데이터가 ${length * 4}바이트여야 하는데 ${bytes.length}바이트예요.`);
    const theta = Float64Array.from({ length }, (_, i) => bytes.readFloatLE(i * 4));
    if (!theta.every(Number.isFinite)) bad('격자 θ에 유한하지 않은 값이 있어요.');
    grid = { kind: g.kind, dims: expected, theta };
  }
  return { branch, m1, grid };
}

/**
 * 서버 기준 렌더러: 사진에 레시피를 건다.
 * @param {Uint8ClampedArray} rgba 입력 사진 RGBA (width × height, sRGB)
 * @param {object} recipe encodeRecipe의 결과(JSON) 또는 JSON.parse한 것
 * @param {{ scaleK?: number }} opts scaleK: 프레임 긴 변 / 원본 긴 변
 * @returns {Uint8ClampedArray} 새 RGBA
 */
export function applyRecipe(rgba, width, height, recipe, { scaleK = 1 } = {}) {
  const { branch, m1, grid } = decodeRecipe(recipe);
  let img;
  if (branch === 'M1') img = applyM1(rgba, width, height, { ...m1, grain: null }, { scaleK });
  else if (branch === 'A2') img = applyA2(rgba, width, height, m1.lut, grid.theta);
  else img = applyG1(applyM1(rgba, width, height, { ...m1, grain: null }, { scaleK }), rgba, width, height, grid.theta);
  if (!m1.grain) return img;
  return applyGrain(img, width, height, m1.grain, new Uint8ClampedArray(width * height * 4));
}
