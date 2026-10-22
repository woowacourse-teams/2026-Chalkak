import { describe, expect, it } from "vitest";

import { captureRect, centerOn, frameRect, MAX_ZOOM, viewfinderIn } from "./camera";
import { onggiScene } from "./scene";
import { checkTitle, isValidShotRect, roundRect } from "./shared-shot";

const sceneAspect = onggiScene.size.width / onggiScene.size.height;

describe("공유 작품 제목", () => {
  it("비어 있거나 공백만 있으면 제목 없음으로 본다", () => {
    expect(checkTitle(undefined)).toEqual({ ok: true, title: null });
    expect(checkTitle("   ")).toEqual({ ok: true, title: null });
  });

  it("앞뒤 공백을 지우고 사이 공백은 하나로 줄인다", () => {
    expect(checkTitle("  김이   오르는 저녁 ")).toEqual({ ok: true, title: "김이 오르는 저녁" });
  });

  it("10자까지 받는다. 이모지도 한 글자로 센다", () => {
    expect(checkTitle("가나다라마바사아자차")).toEqual({ ok: true, title: "가나다라마바사아자차" });
    expect(checkTitle("🐱🐱🐱🐱🐱🐱🐱🐱🐱🐱").ok).toBe(true);
    expect(checkTitle("가나다라마바사아자차카").ok).toBe(false);
  });

  it("욕설·주소·보이지 않는 문자를 막는다", () => {
    expect(checkTitle("씨 발").ok).toBe(false);
    expect(checkTitle("abc.com").ok).toBe(false);
    expect(checkTitle("온기​").ok).toBe(false);
    expect(checkTitle(42).ok).toBe(false);
  });

  it("다른 말의 일부일 뿐인 평범한 제목은 막지 않는다", () => {
    for (const title of ["새끼 고양이", "바라보지 마", "불이 꺼져도", "시바견 산책"]) expect(checkTitle(title).ok).toBe(true);
  });
});

describe("공유 작품 프레임", () => {
  it("체험 카메라가 어느 화면 크기·배율로 찍은 프레임이든 받는다", () => {
    for (const stage of [
      { width: 1100, height: 620 },
      { width: 360, height: 560 },
    ]) {
      const viewfinder = viewfinderIn(stage);
      for (const zoom of [1, 2.5, MAX_ZOOM]) {
        for (const target of [onggiScene.start, { x: 0, y: 0 }, { x: 1, y: 1 }]) {
          const view = centerOn(target, zoom, onggiScene.size, viewfinder);
          expect(isValidShotRect(roundRect(captureRect(view, onggiScene.size, viewfinder)))).toBe(true);
        }
      }
    }
  });

  it("풍경 밖, 9:16이 아닌 비율, 최대 배율보다 작은 프레임은 받지 않는다", () => {
    const valid = frameRect({ cx: 0.5, cy: 0.5, h: 0.5 }, sceneAspect);
    expect(isValidShotRect({ ...valid, x: 0.9 })).toBe(false);
    expect(isValidShotRect({ ...valid, width: valid.width * 2 })).toBe(false);
    expect(isValidShotRect(frameRect({ cx: 0.5, cy: 0.5, h: 0.1 }, sceneAspect))).toBe(false);
    expect(isValidShotRect({ x: "0", y: 0, width: 0.1, height: 0.5 })).toBe(false);
    expect(isValidShotRect(null)).toBe(false);
  });
});
