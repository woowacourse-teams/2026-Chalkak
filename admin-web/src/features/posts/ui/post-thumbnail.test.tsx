import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PostThumbnail } from "./post-thumbnail";

describe("PostThumbnail", () => {
  it("shows an accessible fallback when an image fails", () => {
    render(
      <PostThumbnail
        alt="테스트 게시물 썸네일"
        src="https://images.example.com/missing.jpg"
      />,
    );

    fireEvent.error(screen.getByRole("img", { name: "테스트 게시물 썸네일" }));

    expect(
      screen.getByRole("img", { name: "이미지를 불러올 수 없음" }),
    ).toBeInTheDocument();
  });
});
