import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { userIds } from "@/mocks/fixtures";
import { QueryProvider } from "@/shared/query/query-provider";
import { ToastProvider } from "@/shared/ui/toast";

import { UserDetailScreen } from "./user-detail-screen";
import { UserListScreen } from "./user-list-screen";

const { routerPush, search } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  search: { value: "status=ACTIVE&page=1" },
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/users",
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => new URLSearchParams(search.value),
}));

describe("user management screens", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    routerPush.mockReset();
    search.value = "status=ACTIVE&page=1";
  });

  it("searches by email without asking for a user UUID", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><UserListScreen /></QueryProvider>);
    expect(await screen.findAllByText("creator@example.com")).not.toHaveLength(0);
    expect(screen.queryByPlaceholderText("UUID")).not.toBeInTheDocument();
    await user.type(screen.getByRole("textbox", { name: "이메일 검색" }), "creator");
    await user.click(screen.getByRole("button", { name: "검색 적용" }));
    expect(routerPush).toHaveBeenCalledWith(expect.stringContaining("email=creator"));
  });

  it("defaults to active users and offers only the three status tabs", async () => {
    search.value = "";
    render(<QueryProvider><UserListScreen /></QueryProvider>);
    const tabs = screen.getByRole("navigation", { name: "사용자 상태" });
    expect(within(tabs).getAllByRole("link")).toHaveLength(3);
    expect(within(tabs).getByRole("link", { name: "활성" })).toHaveAttribute("aria-current", "page");
    expect(within(tabs).queryByRole("link", { name: "전체" })).not.toBeInTheDocument();
    expect(within(tabs).getByRole("link", { name: "차단" })).toHaveAttribute("href", "/users?status=BANNED&page=1");
    expect(await screen.findAllByText("creator@example.com")).not.toHaveLength(0);
    expect(screen.queryByText("paused@example.com")).not.toBeInTheDocument();
  });

  it("links all three user counts on desktop and mobile without nested links", async () => {
    render(<QueryProvider><UserListScreen /></QueryProvider>);
    const links = await screen.findAllByRole("link", { name: "검수 대기 게시물 2개 보기" });
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link).toHaveAttribute("href", "/posts?status=PENDING&userId=" + userIds.active + "&returnTo=%2Fusers%3Fstatus%3DACTIVE%26page%3D1");
      expect(link.parentElement?.closest("a")).toBeNull();
    }
    expect(screen.getAllByRole("link", { name: "승인 게시물 10개 보기" })).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: "거절 게시물 1개 보기" })).toHaveLength(2);
  });

  it("links the detail counts and preserves its source filter", async () => {
    search.value = "returnTo=%2Fposts%3Fstatus%3DREJECTED";
    render(<QueryProvider><ToastProvider><UserDetailScreen userId={userIds.active} /></ToastProvider></QueryProvider>);
    const link = await screen.findByRole("link", { name: "거절 게시물 1개 보기" });
    const params = new URL(link.getAttribute("href")!, "http://localhost").searchParams;
    expect(params.get("status")).toBe("REJECTED");
    expect(params.get("userId")).toBe(userIds.active);
    expect(params.get("returnTo")).toBe("/users/" + userIds.active + "?returnTo=%2Fposts%3Fstatus%3DREJECTED");
    expect(screen.getByRole("link", { name: "← 목록과 필터로 돌아가기" })).toHaveAttribute("href", "/posts?status=REJECTED");
  });

  it("requires an audit reason before banning an active user", async () => {
    search.value = "returnTo=%2Fusers%3Fstatus%3DACTIVE";
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><UserDetailScreen userId={userIds.active} /></ToastProvider></QueryProvider>);
    await user.click(await screen.findByRole("button", { name: "사용자 차단" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "차단" })).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: "차단 사유" }), "반복적인 운영 정책 위반");
    await user.click(within(dialog).getByRole("button", { name: "차단" }));
    expect(await screen.findByText("사용자를 차단했습니다.")).toBeInTheDocument();
  });

  it("does not offer status actions for a withdrawn user", async () => {
    render(<QueryProvider><ToastProvider><UserDetailScreen userId={userIds.withdrawn} /></ToastProvider></QueryProvider>);
    expect(await screen.findByText(/탈퇴한 사용자는 기록 확인만 가능/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /차단/ })).not.toBeInTheDocument();
  });
});
