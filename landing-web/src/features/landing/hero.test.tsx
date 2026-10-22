import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { APP_SCREEN_VERSION } from "./app-screens";
import { Hero } from "./hero";

describe("히어로", () => {
  it("서비스 소개와 체험·스토어 링크를 유지한다", () => {
    render(<Hero />);
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("매일 하나의 주제,각자의 한 장");
    expect(screen.getByRole("link", { name: "지금 한 장 찍어보기" })).toHaveAttribute("href", "#experience");
    expect(screen.getByRole("link", { name: "App Store에서 다운로드" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Google Play에서 다운로드" })).toBeInTheDocument();
  });

  it("풍경 대신 실제 앱 화면 세 장을 사용한다", () => {
    render(<Hero />);
    const phones = screen.getByLabelText("찰캌 앱 화면").querySelectorAll("img");
    expect(phones).toHaveLength(3);
    expect([...phones].map((image) => decodeURIComponent(image.src))).toEqual([
      expect.stringContaining("/app-screens/display-feed.webp"),
      expect.stringContaining("/app-screens/today.webp"),
      expect.stringContaining("/app-screens/display-detail.webp"),
    ]);
    phones.forEach((image) => expect(decodeURIComponent(image.src)).toContain(`?v=${APP_SCREEN_VERSION}`));
    expect(phones[0]).toHaveAttribute("loading", "lazy");
    expect(phones[1]).not.toHaveAttribute("loading", "lazy");
    expect(phones[2]).toHaveAttribute("loading", "lazy");
  });
});
