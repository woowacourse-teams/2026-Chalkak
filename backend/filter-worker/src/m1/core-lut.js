// 색 코어에서 33³ LUT를 읽는다. 코어 칸마다 가운데 4×4 픽셀의 평균을 그 칸 색의 출력으로 삼는다.
import { CELL, CORE, CORE_COLS, CORE_ROWS, LAYOUT, N, W } from './layout.js';

export function extractCoreLut(rgba) {
  const lut = new Uint8Array(N * N * N * 3);
  for (let cy = 0; cy < CORE_ROWS; cy++) for (let cx = 0; cx < CORE_COLS; cx++) {
    const c = LAYOUT.coreGrid[cy * CORE_COLS + cx];
    if (c < 0) continue;
    const x0 = CORE.x + cx * CELL + 2, y0 = CORE.y + cy * CELL + 2;
    let r = 0, g = 0, b = 0;
    for (let y = y0; y < y0 + 4; y++) for (let x = x0; x < x0 + 4; x++) { const p = (y * W + x) * 4; r += rgba[p]; g += rgba[p + 1]; b += rgba[p + 2]; }
    lut[c * 3] = Math.round(r / 16);
    lut[c * 3 + 1] = Math.round(g / 16);
    lut[c * 3 + 2] = Math.round(b / 16);
  }
  return lut;
}
