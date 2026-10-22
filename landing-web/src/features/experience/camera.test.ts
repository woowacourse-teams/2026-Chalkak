import { describe, expect, it } from "vitest";

import {
  captureRect,
  centerOn,
  clampView,
  cropBackground,
  frameRect,
  MAX_ZOOM,
  minScale,
  overlapRatio,
  viewfinderIn,
  zoomAt,
  zoomOf,
} from "./camera";

const scene = { width: 2000, height: 1000 };
const stage = { width: 1000, height: 800 };

describe("viewfinderIn", () => {
  it("무대 높이의 90%를 쓰는 9:16 뷰파인더를 가운데에 둔다", () => {
    const vf = viewfinderIn(stage);
    expect(vf.height).toBeCloseTo(720);
    expect(vf.width).toBeCloseTo(405);
    expect(vf.x).toBeCloseTo((1000 - 405) / 2);
    expect(vf.y).toBeCloseTo(40);
  });

  it("좁은 무대에서는 폭에 맞춰 줄인다", () => {
    const vf = viewfinderIn({ width: 300, height: 800 });
    expect(vf.width).toBeCloseTo(258);
    expect(vf.width / vf.height).toBeCloseTo(9 / 16);
  });
});

describe("clampView", () => {
  const vf = viewfinderIn(stage);
  const min = minScale(scene, vf);

  it("배율을 1.0x와 최대 배율 사이로 제한한다", () => {
    expect(clampView({ x: 0, y: 0, scale: min / 2 }, scene, vf).scale).toBeCloseTo(min);
    expect(clampView({ x: 0, y: 0, scale: min * 10 }, scene, vf).scale).toBeCloseTo(min * MAX_ZOOM);
  });

  it("뷰파인더가 풍경 밖을 비추지 않게 위치를 제한한다", () => {
    const view = clampView({ x: 9999, y: -9999, scale: min }, scene, vf);
    const rect = captureRect(view, scene, vf);
    expect(rect.x).toBeCloseTo(0);
    expect(rect.y + rect.height).toBeCloseTo(1);
  });
});

describe("zoomAt", () => {
  it("기준점 아래의 풍경 위치를 유지한 채 확대한다", () => {
    const vf = viewfinderIn(stage);
    const start = centerOn({ x: 0.5, y: 0.5 }, 1, scene, vf);
    const anchor = { x: vf.x + vf.width / 2, y: vf.y + vf.height / 2 };
    const before = { x: (anchor.x - start.x) / start.scale, y: (anchor.y - start.y) / start.scale };
    const zoomed = zoomAt(start, start.scale * 2, anchor, scene, vf);
    expect(zoomOf(zoomed, scene, vf)).toBeCloseTo(2);
    expect((anchor.x - zoomed.x) / zoomed.scale).toBeCloseTo(before.x);
    expect((anchor.y - zoomed.y) / zoomed.scale).toBeCloseTo(before.y);
  });
});

describe("captureRect", () => {
  it("1.0x에서는 풍경 높이 전체를 9:16으로 담는다", () => {
    const vf = viewfinderIn(stage);
    const rect = captureRect(centerOn({ x: 0.5, y: 0.5 }, 1, scene, vf), scene, vf);
    expect(rect.height).toBeCloseTo(1);
    expect(rect.width).toBeCloseTo((1000 * 9) / 16 / 2000);
    expect(rect.x + rect.width / 2).toBeCloseTo(0.5);
  });
});

describe("frameRect", () => {
  it("가장자리 프레임을 풍경 안으로 밀어 넣는다", () => {
    const rect = frameRect({ cx: 0.99, cy: 0.02, h: 0.4 }, 2);
    expect(rect.x + rect.width).toBeCloseTo(1);
    expect(rect.y).toBe(0);
    expect((rect.width * 2) / rect.height).toBeCloseTo(9 / 16);
  });
});

describe("overlapRatio", () => {
  const a = { x: 0, y: 0, width: 0.2, height: 0.4 };

  it("같은 프레임은 1, 떨어진 프레임은 0이다", () => {
    expect(overlapRatio(a, a)).toBeCloseTo(1);
    expect(overlapRatio(a, { ...a, x: 0.5 })).toBe(0);
  });

  it("절반이 겹치면 1/3이다", () => {
    expect(overlapRatio(a, { ...a, x: 0.1 })).toBeCloseTo(1 / 3);
  });
});

describe("cropBackground", () => {
  it("비율 사각형을 배경 크기와 위치로 바꾼다", () => {
    expect(cropBackground({ x: 0.25, y: 0, width: 0.5, height: 1 })).toEqual({
      backgroundSize: "200% auto",
      backgroundPosition: "50% 0%",
    });
  });
});
