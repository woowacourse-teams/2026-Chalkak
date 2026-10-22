import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { APP_SCREEN_VERSION } from "./app-screens";
import { AppGuide } from "./app-guide";

describe("사용법", () => {
  it("앱 사용 순서 다섯 단계와 설명을 유지한다", () => {
    render(<AppGuide />);
    expect(screen.getAllByRole("listitem")).toHaveLength(5);
    expect(screen.getAllByRole("heading", { level: 3 }).map((heading) => heading.textContent)).toEqual([
      "나만의 사인을 그려요",
      "오늘의 주제를 받아요",
      "나만의 한 장을 올려요",
      "다른 시선을 감상해요",
      "좋아요를 남기고, 기록을 모아요",
    ]);
    expect(screen.getByText(/사인은 언제든 다시 그릴 수 있어요/)).toBeInTheDocument();
  });

  it("다섯 단계 모두 실제 앱 캡처를 직접 표시한다", () => {
    const { container } = render(<AppGuide />);
    const images = [...container.querySelectorAll("#how img")];
    expect(images).toHaveLength(5);
    expect(images.map((image) => decodeURIComponent(image.getAttribute("src")!))).toEqual([
      expect.stringContaining("/app-screens/signature.webp"),
      expect.stringContaining("/app-screens/today.webp"),
      expect.stringContaining("/app-screens/upload.webp"),
      expect.stringContaining("/app-screens/display-feed.webp"),
      expect.stringContaining("/app-screens/record.webp"),
    ]);
    images.forEach((image) => {
      expect(decodeURIComponent(image.getAttribute("src")!)).toContain(`?v=${APP_SCREEN_VERSION}`);
      expect(image).toHaveAttribute("width", "642");
      expect(image).toHaveAttribute("height", "1396");
    });
  });
});
