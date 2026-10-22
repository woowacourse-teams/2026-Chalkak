import { describe, expect, it } from "vitest";

import { clampRatio, mapNormalizedPoint, normalizePoint, PREVIEW_STROKES, signatureLayout, smoothPath } from "./signature";

describe("signature", () => {
  it("clamps normalized points", () => {
    expect(clampRatio(-1)).toBe(0);
    expect(clampRatio(1.4)).toBe(1);
    expect(normalizePoint({ x: -10, y: 120 }, { left: 0, top: 0, width: 100, height: 100 })).toEqual({ x: 0, y: 1 });
  });

  it.each([
    [[], 0],
    [[{ x: 0.2, y: 0.3 }], 1],
    [[{ x: 0.2, y: 0.3 }, { x: 0.8, y: 0.7 }], 2],
    [[{ x: 0.2, y: 0.3 }, { x: 0.4, y: 0.5 }, { x: 0.8, y: 0.7 }], 3],
  ] as const)("handles %i-point strokes", (points, count) => {
    expect(smoothPath([...points])).toHaveLength(count);
  });

  it("uses the iOS minimum bbox and centers a single point", () => {
    const layout = signatureLayout([[{ x: 0.5, y: 0.5 }]])!;
    expect(layout.scale).toBeCloseTo(416 / 0.02);
    expect(layout.offsetX).toBeCloseTo(304);
    expect(layout.offsetY).toBeCloseTo(48);
  });

  it("keeps a pad point at the finger's normalized position", () => {
    expect(mapNormalizedPoint({ x: 0.8, y: 0.2 }, 300, 300)).toEqual({ x: 240, y: 60 });
  });

  it("fits horizontal and vertical strokes inside the padded output", () => {
    const horizontal = signatureLayout([[{ x: 0, y: 0.5 }, { x: 1, y: 0.5 }]])!;
    const vertical = signatureLayout([[{ x: 0.5, y: 0 }, { x: 0.5, y: 1 }]])!;
    expect(horizontal.offsetX).toBeCloseTo(48);
    expect(vertical.offsetY).toBeCloseTo(48);
  });

  it("matches the iOS quadratic smoothing recipe", () => {
    expect(smoothPath(PREVIEW_STROKES[0])).toEqual([
      { type: "move", point: { x: 0.12, y: 0.64 } },
      { type: "quad", control: { x: 0.24, y: 0.52 }, point: { x: 0.31, y: 0.6000000000000001 } },
      { type: "quad", control: { x: 0.38, y: 0.68 }, point: { x: 0.465, y: 0.5800000000000001 } },
      { type: "quad", control: { x: 0.55, y: 0.48 }, point: { x: 0.665, y: 0.52 } },
      { type: "line", point: { x: 0.78, y: 0.56 } },
    ]);
  });

  it("fits the full signature in a padded 1024x512 canvas", () => {
    const layout = signatureLayout(PREVIEW_STROKES);
    expect(layout?.offsetX).toBeGreaterThanOrEqual(48);
    expect(layout?.offsetY).toBeGreaterThanOrEqual(48);
    expect(layout?.scale).toBeCloseTo(928 / 0.66, 5);
  });
});
