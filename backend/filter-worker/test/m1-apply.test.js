// M1 적용 스택: 시험 지원 코드의 독립 구현(test/support/apply-stack.js)과 한 픽셀도 다르지 않아야 한다.
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { applyM1 } from '../src/m1/apply.js';
import { KIND_AMP0, makeBandGain } from '../src/m1/detail.js';
import { GRAIN_L_KNOTS } from '../src/m1/grain.js';
import { applyStack, vignetteFromSeparable } from './support/apply-stack.js';
import { curvedLut } from './support/grid-fixtures.js';
import { makeScene } from './support/scenes.js';

const SW = 192, SH = 256;

function recipe() {
  const K = makeBandGain(KIND_AMP0);
  K.K.forEach((_, i) => { K.K[i] = 0.05 * Math.sin(i); });
  return {
    lut: curvedLut(),
    V: vignetteFromSeparable([0, -0.1, -0.25, -0.45, -0.7, -0.9, -1.1, -1.3], [1, 1, 1, 1, 1, 1, 1, 1], 'dark'),
    C: { ny: 5, yLo: 0, yHi: 100, nb: 3, bLo: -7, bHi: 0, sigmaPct: 0.01, T: Float64Array.from({ length: 15 }, (_, i) => 3 * Math.cos(i)) },
    K,
    grain: { amp: GRAIN_L_KNOTS.map(() => 1.5), size: 0.004, edge: 4, tilt: 0 },
  };
}

describe('applyM1', () => {
  const img = makeScene(0, SW, SH), m1 = recipe(), all = { C: true, K: true, V: true, grain: true };

  test('C·k_b·V·그레인 전부 켠 스택이 시험 지원 구현과 같다', () => {
    const got = applyM1(img, SW, SH, m1), want = applyStack(img, SW, SH, m1, all);
    assert.deepEqual(got, want);
    assert.notDeepEqual(got, applyM1(img, SW, SH, m1, { grain: false }));
  });

  test('꺼진 연산자(null)는 건너뛰고, 그레인 끄기는 그레인만 뺀다', () => {
    const lutOnly = { lut: m1.lut, V: null, C: null, K: null, grain: null };
    assert.deepEqual(applyM1(img, SW, SH, lutOnly), applyStack(img, SW, SH, lutOnly, all));
    assert.deepEqual(applyM1(img, SW, SH, m1, { grain: false }), applyStack(img, SW, SH, m1, { ...all, grain: false }));
  });

  test('scaleK는 대역 이득 σ를 키운다', () => {
    const a = applyM1(img, SW, SH, m1, { grain: false }), b = applyM1(img, SW, SH, m1, { scaleK: 2, grain: false });
    assert.notDeepEqual(a, b);
  });
});
