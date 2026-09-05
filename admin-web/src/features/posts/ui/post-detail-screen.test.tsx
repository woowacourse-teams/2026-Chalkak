import { render, screen, waitFor, within } from "@testing-library/react";
import { QueryClientProvider } from "@tanstack/react-query";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";

import { postIds, topicIds, userIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import { QueryProvider } from "@/shared/query/query-provider";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";

import { PostDetailScreen } from "./post-detail-screen";

const { routerPush, search } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  search: { value: "returnTo=%2Fposts%3Fstatus%3DPENDING" },
}));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(search.value),
  useRouter: () => ({ push: routerPush }),
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
    routerPush.mockReset();
    search.value = "returnTo=%2Fposts%3Fstatus%3DPENDING";
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
    expect(routerPush).toHaveBeenCalledWith("/posts?status=PENDING");
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
      await screen.findByRole("link", { name: "← 이전 목록으로 돌아가기" }),
    ).toHaveAttribute("href", "/posts?status=PENDING");
  });

  it("preserves audit filters on the back link and after successful moderation", async () => {
    const returnTo = "/audit-logs?action=POST_REJECTED&targetType=POST&page=3";
    search.value = new URLSearchParams({ returnTo }).toString();
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);

    expect(await screen.findByRole("link", { name: "← 이전 목록으로 돌아가기" })).toHaveAttribute("href", returnTo);
    await user.click(screen.getByRole("button", { name: "승인" }));
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "승인" }));
    expect(await screen.findByText("게시물을 승인했습니다.")).toBeInTheDocument();
    expect(routerPush).toHaveBeenCalledWith(returnTo);
  });

  it.each([
    "https://example.com/audit-logs",
    "//example.com/posts",
    "/users?status=ACTIVE",
    "/audit-logs/unrelated",
    "/posts-other?status=PENDING",
  ])("rejects an unsupported return destination: %s", async (returnTo) => {
    search.value = new URLSearchParams({ returnTo }).toString();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.approved} /></ToastProvider></QueryProvider>);
    expect(await screen.findByRole("link", { name: "← 이전 목록으로 돌아가기" })).toHaveAttribute("href", "/posts?status=PENDING");
  });

  it("does not offer moderation for an unavailable post", async () => {
    render(
      <QueryProvider>
        <ToastProvider>
          <PostDetailScreen postId="missing" />
        </ToastProvider>
      </QueryProvider>,
    );

    expect(
      await screen.findByRole("heading", {
        name: "게시물을 찾을 수 없습니다",
      }, { timeout: 3000 }),
    ).toBeInTheDocument();
    expect(screen.queryByText("이미지 처리 중")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "승인" })).not.toBeInTheDocument();
  });

  it("links the topic and author back to the filtered post list", async () => {
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);
    expect(await screen.findByRole("link", { name: /오늘의 빛/ })).toHaveAttribute("href", "/topics/" + topicIds.open + "?returnTo=%2Fposts%3Fstatus%3DPENDING");
    expect(screen.getByRole("link", { name: "creator@example.com" })).toHaveAttribute("href", "/users/" + userIds.active + "?returnTo=%2Fposts%3Fstatus%3DPENDING");
    expect(screen.getByRole("group", { name: "게시물 작업" })).toBeInTheDocument();
  });

  it("requires a rejection reason and returns to the previous filters", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);
    await user.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "거절" })).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: "거절 사유" }), "운영 정책 위반");
    await user.click(within(dialog).getByRole("button", { name: "거절" }));
    expect(await screen.findByText("게시물을 거절했습니다.")).toBeInTheDocument();
    expect(routerPush).toHaveBeenCalledWith("/posts?status=PENDING");
  });

  it("keeps only moderation in the primary bar and separates deletion under extra information", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);
    const actions = await screen.findByRole("group", { name: "게시물 작업" });
    expect(within(actions).getAllByRole("button").map((button) => button.textContent)).toEqual(["거절", "승인"]);
    expect(actions.closest("[data-review-mode]")).toHaveAttribute("data-review-mode", "true");
    await user.click(screen.getByText("추가 정보"));
    const deletion = screen.getByRole("button", { name: "게시물 삭제" });
    expect(actions).not.toContainElement(deletion);
    await user.click(deletion);
    expect(screen.getByRole("dialog", { name: "이 게시물을 삭제할까요?" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "삭제" })).toBeDisabled();
  });

  it("keeps a failed rejection and its reason inside the dialog so retry remains visible", async () => {
    server.use(http.put("*/api/v1/admin/posts/:postId/moderation", () => HttpResponse.json({ errorCode: "INTERNAL_SERVER_ERROR", message: "잠시 후 다시 시도해 주세요." }, { status: 503 }), { once: true }));
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);
    await user.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: "거절 사유" }), "주제와 다른 사진\n내용 확인 필요");
    await user.click(within(dialog).getByRole("button", { name: "거절" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("잠시 후 다시 시도해 주세요.");
    expect(within(dialog).getByRole("textbox", { name: "거절 사유" })).toHaveValue("주제와 다른 사진\n내용 확인 필요");
    expect(routerPush).not.toHaveBeenCalled();
    await user.click(within(dialog).getByRole("button", { name: "거절" }));
    expect(await screen.findByText("게시물을 거절했습니다.")).toBeInTheDocument();
  });

  it("retains the reason when the follow-up refresh also fails and allows retry afterwards", async () => {
    const client = createQueryClient();
    client.setDefaultOptions({ queries: { retry: false } });
    const user = userEvent.setup();
    render(<QueryClientProvider client={client}><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryClientProvider>);
    await user.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: "거절 사유" }), "다시 확인해 주세요.");
    server.use(
      http.put("*/api/v1/admin/posts/:postId/moderation", () => HttpResponse.json({ message: "잠시 후 다시 시도해 주세요." }, { status: 503 }), { once: true }),
      http.get("*/api/v1/admin/posts/:postId", () => HttpResponse.json({ message: "일시적인 연결 오류입니다." }, { status: 503 }), { once: true }),
    );
    await user.click(within(dialog).getByRole("button", { name: "거절" }));
    await waitFor(() => expect(client.getQueryState(queryKeys.posts.detail(postIds.pending))).toMatchObject({ status: "error", fetchStatus: "idle" }));
    expect(dialog).toBeInTheDocument();
    expect(within(dialog).getByRole("textbox", { name: "거절 사유" })).toHaveValue("다시 확인해 주세요.");
    await user.click(within(dialog).getByRole("button", { name: "거절" }));
    expect(await screen.findByText("게시물을 거절했습니다.")).toBeInTheDocument();
  });

  it("keeps confirmation and cancel disabled until a failed action's refresh settles", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><PostDetailScreen postId={postIds.pending} /></ToastProvider></QueryProvider>);
    await user.click(await screen.findByRole("button", { name: "거절" }));
    const dialog = screen.getByRole("dialog");
    await user.type(within(dialog).getByRole("textbox", { name: "거절 사유" }), "내용 확인 필요");
    let completeRefresh: (() => void) | undefined;
    const refreshGate = new Promise<void>((resolve) => { completeRefresh = resolve; });
    const refreshStarted = vi.fn();
    server.use(
      http.put("*/api/v1/admin/posts/:postId/moderation", () => HttpResponse.json({ message: "다시 시도해 주세요." }, { status: 503 }), { once: true }),
      http.get("*/api/v1/admin/posts/:postId", async () => {
        refreshStarted();
        await refreshGate;
        return HttpResponse.json({ message: "일시적인 연결 오류입니다." }, { status: 503 });
      }, { once: true }),
    );
    try {
      await user.click(within(dialog).getByRole("button", { name: "거절" }));
      await waitFor(() => expect(refreshStarted).toHaveBeenCalledOnce());
      expect(within(dialog).getByRole("button", { name: "처리 중…" })).toBeDisabled();
      expect(within(dialog).getByRole("button", { name: "취소" })).toBeDisabled();
    } finally {
      completeRefresh?.();
    }
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "거절" })).toBeEnabled(), { timeout: 3000 });
  });
});
