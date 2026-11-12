// 시험용: 견본에 연산을 걸어 "편집본 견본"을 만든다.
import { CORE, N, W, gridValue } from '../../src/m1/layout.js';

export const planOf = (on) => ({ on: { V: false, C: false, K: false, grain: false, ...on } });

// op로 견본 전체를 편집하되 색 코어는 입력 그대로 둔다(LUT가 연산을 흡수하지 않는 경우)
export function renderOutsideCore(probe, op) {
  const r = op(probe);
  for (let y = CORE.y; y < CORE.y + CORE.h; y++) r.set(probe.subarray((y * W + CORE.x) * 4, (y * W + CORE.x + CORE.w) * 4), (y * W + CORE.x) * 4);
  return r;
}

// 33³ 항등 LUT (r이 가장 빨리 변하는 순서)
export function identityLut() {
  const lut = new Uint8Array(N ** 3 * 3);
  for (let c = 0; c < N ** 3; c++) {
    lut[c * 3] = gridValue(c % N);
    lut[c * 3 + 1] = gridValue(Math.floor(c / N) % N);
    lut[c * 3 + 2] = gridValue(Math.floor(c / (N * N)));
  }
  return lut;
}
