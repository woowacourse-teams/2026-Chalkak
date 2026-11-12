// 레시피 JSON: 인코딩 → JSON 문자열 → 디코딩 → applyRecipe가 메모리 안 구조를 직접 적용한 결과와 같은지 본다.
// M1은 숫자가 JSON을 정확히 왕복하므로 픽셀이 같고, 격자 θ는 f32를 거치므로 최대 1단계 차이를 허용한다.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyA2, applyG1 } from '../src/grid/apply.js';
import { fitA2, fitG1 } from '../src/grid/fit.js';
import { probeBase } from '../src/grid/samples.js';
import { applyM1 } from '../src/m1/apply.js';
import { KIND_AMP0, makeBandGain } from '../src/m1/detail.js';
import { applyGrain } from '../src/m1/grain.js';
import { C_GRID } from '../src/m1/local-tone.js';
import { EXTRACTOR_VERSION, RecipeFormatError, applyRecipe, decodeRecipe, encodeRecipe } from '../src/recipe.js';
import { applyStack, vignetteFromSeparable } from './support/apply-stack.js';
import { curvedLut } from './support/grid-fixtures.js';
import { makeScene } from './support/scenes.js';

const SW = 384, SH = 512;
const frame = (rgba) => ({ rgba, width: SW, height: SH });
const VIG = { ...vignetteFromSeparable([0, -0.1, -0.25, -0.45, -0.7, -0.9, -1.1, -1.3], [1, 1, 1, 1, 1, 1, 1, 1], 'dark'), shape: { p: 2, c: 1 } };
const C = { ...C_GRID, sigmaPct: 0.02, T: Float64Array.from({ length: C_GRID.ny * C_GRID.nb }, (_, i) => -2 * Math.sin(i * 0.37) || 0) };
const K = makeBandGain(KIND_AMP0);
K.K.forEach((_, i) => { K.K[i] = 0.3 / (1 + (i % 7)); });
const GRAIN = { amp: [0.6, 0.8, 1, 1, 1, 0.9, 0.7, 0.5], size: 0.004, edge: 3, tilt: 0.5, seed: 3 };

const maxDiff = (a, b) => { let m = 0; for (let i = 0; i < a.length; i++) m = Math.max(m, Math.abs(a[i] - b[i])); return m; };
const viaJson = (recipe) => JSON.parse(JSON.stringify(recipe));

describe('레시피 JSON', () => {
  const scene = makeScene(1, SW, SH), identity = frame(scene), base = probeBase(identity);
  const lut = curvedLut();
  const m1 = { lut, V: VIG, C, K, grain: GRAIN };
  const edited = frame(applyStack(scene, SW, SH, { lut, V: VIG }, { V: true }));

  test('M1: 연산자 전부 켠 레시피가 픽셀까지 같다', () => {
    const json = viaJson(encodeRecipe({ branch: 'M1', m1 }));
    assert.equal(json.schemaVersion, 1);
    assert.equal(json.extractorVersion, EXTRACTOR_VERSION);
    assert.equal(json.probeVersion, 2);
    assert.equal(json.colorSpace, 'sRGB');
    assert.deepEqual(Object.keys(json.ops).sort(), ['detail', 'grain', 'localTone', 'lut', 'vignette']);
    assert.deepEqual([json.ops.lut.size, json.ops.lut.encoding], [33, 'rgb8']);
    for (const scaleK of [1, 0.4]) assert.deepEqual(applyRecipe(scene, SW, SH, json, { scaleK }), applyM1(scene, SW, SH, m1, { scaleK }));
  });

  test('디코드한 구조가 원래 연산자와 같다', () => {
    const d = decodeRecipe(viaJson(encodeRecipe({ branch: 'M1', m1 })));
    assert.deepEqual(d.m1.lut, lut);
    assert.deepEqual(d.m1.V, VIG);
    assert.deepEqual(d.m1.C, C);
    assert.deepEqual(d.m1.K, K);
    assert.deepEqual(d.m1.grain, GRAIN);
    assert.equal(d.grid, null);
  });

  test('꺼진 연산자는 레시피에 없고, 그레인 시드 기본값은 1', () => {
    const json = encodeRecipe({ branch: 'M1', m1: { lut, V: null, C: null, K: null, grain: { ...GRAIN, seed: undefined } } });
    assert.deepEqual(Object.keys(json.ops).sort(), ['grain', 'lut']);
    assert.equal(json.ops.grain.seed, 1);
    assert.deepEqual(Object.keys(encodeRecipe({ branch: 'M1', m1: { lut, V: null, C: null, K: null, grain: null } }).ops), ['lut']);
  });

  test('A2: LUT → 격자 → 그레인. 비네팅·톤·디테일은 담지 않고 f32 θ로 최대 1단계 차이', () => {
    const grid = fitA2(identity, edited, m1, { base });
    const json = viaJson(encodeRecipe({ branch: 'A2', m1, grid }));
    assert.deepEqual(Object.keys(json.ops).sort(), ['grain', 'grid', 'lut']);
    assert.deepEqual([json.ops.grid.kind, json.ops.grid.encoding, json.ops.grid.dims], ['A2', 'f32le', [5, 9, 7, 4, 3]]);
    assert.equal(Buffer.from(json.ops.grid.data, 'base64').length, 15120); // 1260 float32
    const expected = applyGrain(applyA2(scene, SW, SH, lut, grid.theta), SW, SH, GRAIN, new Uint8ClampedArray(SW * SH * 4));
    const diff = maxDiff(applyRecipe(scene, SW, SH, json), expected);
    assert.ok(diff <= 1, `최대 픽셀 차 ${diff}`);
  });

  test('G1: M1 전체(그레인 없이) → 격자 → 그레인, f32 θ로 최대 1단계 차이', () => {
    const grid = fitG1(identity, edited, { ...m1, grain: null }, { base });
    const json = viaJson(encodeRecipe({ branch: 'G1', m1, grid }));
    assert.deepEqual(Object.keys(json.ops).sort(), ['detail', 'grain', 'grid', 'localTone', 'lut', 'vignette']);
    assert.equal(Buffer.from(json.ops.grid.data, 'base64').length, 3600); // 900 float32
    for (const scaleK of [1, 0.5]) {
      const m1Img = applyM1(scene, SW, SH, m1, { scaleK, grain: false });
      const expected = applyGrain(applyG1(m1Img, scene, SW, SH, grid.theta), SW, SH, GRAIN, new Uint8ClampedArray(SW * SH * 4));
      const diff = maxDiff(applyRecipe(scene, SW, SH, json, { scaleK }), expected);
      assert.ok(diff <= 1, `scaleK ${scaleK} 최대 픽셀 차 ${diff}`);
    }
  });

  test('그레인이 없는 격자 레시피는 격자 출력 그대로', () => {
    const grid = fitA2(identity, edited, m1, { base });
    const json = viaJson(encodeRecipe({ branch: 'A2', m1: { ...m1, grain: null }, grid }));
    assert.equal('grain' in json.ops, false);
    assert.ok(maxDiff(applyRecipe(scene, SW, SH, json), applyA2(scene, SW, SH, lut, grid.theta)) <= 1);
  });

  test('깨진 레시피는 RecipeFormatError로 거부한다', () => {
    const ok = viaJson(encodeRecipe({ branch: 'M1', m1 }));
    const rejects = (json, pattern) => assert.throws(() => decodeRecipe(json), (e) => e instanceof RecipeFormatError && e.message.startsWith('레시피 형식 오류: ') && pattern.test(e.message));
    rejects({ ...ok, schemaVersion: 2 }, /버전/);
    rejects({ ...ok, branch: 'X9' }, /분기/);
    rejects({ ...ok, ops: { ...ok.ops, lut: { ...ok.ops.lut, data: 'AAAA' } } }, /LUT 길이/);
    const g = viaJson(encodeRecipe({ branch: 'A2', m1, grid: fitA2(identity, edited, m1, { base }) }));
    rejects({ ...g, ops: { ...g.ops, grid: { ...g.ops.grid, data: 'AAAA' } } }, /격자 데이터/);
    rejects({ ...g, branch: 'G1' }, /격자 형식/);
    // NaN θ: float32 NaN 바이트를 첫 칸에 넣는다
    const nan = Buffer.from(g.ops.grid.data, 'base64');
    nan.writeFloatLE(NaN, 0);
    rejects({ ...g, ops: { ...g.ops, grid: { ...g.ops.grid, data: nan.toString('base64') } } }, /θ/);
    // 그레인 amp 길이, 표 안의 유한하지 않은 값(JSON에서 null)과 길이
    rejects({ ...ok, ops: { ...ok.ops, grain: { ...ok.ops.grain, amp: ok.ops.grain.amp.slice(0, 7) } } }, /grain\.amp 길이/);
    rejects({ ...ok, ops: { ...ok.ops, grain: { ...ok.ops.grain, size: null } } }, /grain\.size/);
    rejects({ ...ok, ops: { ...ok.ops, vignette: { ...ok.ops.vignette, T: [...ok.ops.vignette.T.slice(1), null] } } }, /vignette\.T/);
    rejects({ ...ok, ops: { ...ok.ops, localTone: { ...ok.ops.localTone, T: ok.ops.localTone.T.slice(1) } } }, /localTone\.T 길이/);
    rejects({ ...ok, ops: { ...ok.ops, detail: { ...ok.ops.detail, K: ['a', ...ok.ops.detail.K.slice(1)] } } }, /detail\.K/);
  });
});
