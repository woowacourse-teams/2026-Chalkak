import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { postIds } from "@/mocks/fixtures";
import { QueryProvider } from "@/shared/query/query-provider";
import { ToastProvider } from "@/shared/ui/toast";

import { PostDetailScreen } from "./post-detail-screen";

vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams("returnTo=%2Fposts%3Fstatus%3DPENDING"),
}));

describe("PostDetailScreen", () => {
  beforeEach(() => {
    vi.stubEnv(
      "NEXT_PUBLIC_ADMIN_API_BASE_URL",
      "http://localhost:8080/api/v1/admin",
    );
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it("shows actions only for a pending post and approves once", async () => {
    const user = userEvent.setup();
    render(
      <QueryProvider>
        <ToastProvider>
          <PostDetailScreen postId={postIds.pending} />
        </ToastProvider>
      </QueryProvider>,
    );

    expect(await screen.findByText("검수 대기")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "승인" }));

    const dialog = screen.getByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "승인" }));

    expect(await screen.findByText("게시물을 승인했습니다.")).toBeInTheDocument();
    expect(screen.getByText("승인")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "거절" }),
    ).not.toBeInTheDocument();
  });

  it("keeps the list return URL on the back link", async () => {
    render(
      <QueryProvider>
        <ToastProvider>
          <PostDetailScreen postId={postIds.approved} />
        </ToastProvider>
      </QueryProvider>,
    );

    expect(
      await screen.findByRole("link", { name: "← 목록과 필터로 돌아가기" }),
    ).toHaveAttribute("href", "/posts?status=PENDING");
  });

  it("does not expose a post before image validation is complete", async () => {
    render(
      <QueryProvider>
        <ToastProvider>
          <PostDetailScreen postId={postIds.validating} />
        </ToastProvider>
      </QueryProvider>,
    );

    expect(
      await screen.findByRole("heading", {
        name: "아직 검수할 수 없는 게시물입니다",
      }),
    ).toBeInTheDocument();
    expect(screen.queryByText("이미지 처리 중")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "승인" })).not.toBeInTheDocument();
  });
});
