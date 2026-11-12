// 시험용: 격자 보정 시험이 쓰는 알려진 LUT와 편집본
import { N, gridValue } from '../../src/m1/layout.js';

// 감마 곡선을 낀 33³ LUT (r이 가장 빨리 변하는 순서). 채널마다 다른 곡선이라 색이 함께 기운다
export function curvedLut() {
  const lut = new Uint8Array(N ** 3 * 3), gam = [0.85, 1, 1.2];
  for (let c = 0; c < N ** 3; c++) {
    const v = [gridValue(c % N), gridValue(Math.floor(c / N) % N), gridValue(Math.floor(c / (N * N)))];
    for (let ch = 0; ch < 3; ch++) lut[c * 3 + ch] = Math.round(255 * Math.pow(v[ch] / 255, gam[ch]));
  }
  return lut;
}
