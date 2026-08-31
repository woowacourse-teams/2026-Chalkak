import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { PostCountLinks } from "./post-count-links";

describe("PostCountLinks", () => {
  it("renders only the three backend-supported counts even if extra fields arrive", () => {
    const counts = { pending: 2, approved: 4, rejected: 1, total: 999, validating: 500 };
    render(<PostCountLinks counts={counts} scope={{ userId: "user-id" }} returnTo="/users?status=ACTIVE" />);
    expect(screen.getAllByRole("link")).toHaveLength(3);
    expect(screen.queryByText("999")).not.toBeInTheDocument();
    expect(screen.queryByText("500")).not.toBeInTheDocument();
    expect(screen.queryByText(/전체|이미지 검증/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "승인 게시물 4개 보기" })).toHaveAttribute("href", "/posts?status=APPROVED&userId=user-id&returnTo=%2Fusers%3Fstatus%3DACTIVE");
  });
});
