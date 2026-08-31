import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminShell } from "./admin-shell";

const { state, navigation, logout } = vi.hoisted(() => ({
  state: { admin: { adminId: 1, username: "operator" }, isMock: false },
  navigation: { pathname: "/posts", replace: vi.fn() },
  logout: vi.fn<() => Promise<void>>(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
  useRouter: () => ({ replace: navigation.replace }),
}));

vi.mock("@/features/auth/admin-session-provider", () => ({
  useAdminSession: () => ({ ...state, logout }),
}));

describe("AdminShell", () => {
  beforeEach(() => {
    state.admin.username = "operator";
    state.isMock = false;
    navigation.pathname = "/posts";
    navigation.replace.mockReset();
    logout.mockReset().mockResolvedValue(undefined);
  });

  it("shows only the four working sections and the authenticated account", () => {
    render(<AdminShell><p>게시물 콘텐츠</p></AdminShell>);

    const nav = screen.getByRole("navigation", { name: "관리자 메뉴" });
    expect(within(nav).getAllByRole("link").map((link) => link.textContent)).toEqual(["게시물", "사용자", "주제", "처리 이력"]);
    expect(within(nav).getByRole("link", { name: "게시물" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByText("operator")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "대시보드" })).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "알림" })).not.toBeInTheDocument();
  });

  it("preserves section navigation on a detail route", () => {
    navigation.pathname = "/users/27";
    render(<AdminShell><p>사용자 상세</p></AdminShell>);

    expect(screen.getByRole("link", { name: "사용자" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "게시물" })).not.toHaveAttribute("aria-current");
  });

  it("uses the same four keyboard-accessible mobile links without a drawer", async () => {
    const previousWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", { configurable: true, value: 320 });
    const user = userEvent.setup();
    render(<AdminShell><p>모바일 콘텐츠</p></AdminShell>);

    expect(screen.getAllByRole("navigation", { name: "관리자 메뉴" })).toHaveLength(1);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.tab();
    expect(screen.getByRole("link", { name: "본문으로 바로가기" })).toHaveFocus();
    await user.tab();
    await user.tab();
    expect(screen.getByRole("link", { name: "게시물" })).toHaveFocus();
    await user.tab();
    expect(screen.getByRole("link", { name: "사용자" })).toHaveFocus();

    Object.defineProperty(window, "innerWidth", { configurable: true, value: previousWidth });
  });

  it("labels demo data explicitly", () => {
    state.isMock = true;
    render(<AdminShell><p>게시물 콘텐츠</p></AdminShell>);

    expect(screen.getByText("데모 환경 · 실제 서비스에 영향을 주지 않습니다.")).toBeInTheDocument();
  });

  it("logs out and navigates to login", async () => {
    render(<AdminShell><p>게시물 콘텐츠</p></AdminShell>);

    await userEvent.setup().click(screen.getByRole("button", { name: "로그아웃" }));

    expect(logout).toHaveBeenCalledOnce();
    expect(navigation.replace).toHaveBeenCalledWith("/login");
  });

  it("disables logout while the request is pending", async () => {
    let finishLogout!: () => void;
    logout.mockImplementationOnce(() => new Promise<void>((resolve) => { finishLogout = resolve; }));
    render(<AdminShell><p>게시물 콘텐츠</p></AdminShell>);

    await userEvent.setup().click(screen.getByRole("button", { name: "로그아웃" }));

    expect(screen.getByRole("button", { name: "로그아웃 중" })).toBeDisabled();
    await act(async () => { finishLogout(); });
    expect(logout).toHaveBeenCalledOnce();
  });

  it("routes failed logout requests to a persistent warning", async () => {
    logout.mockRejectedValueOnce(new Error("network"));
    render(<AdminShell><p>게시물 콘텐츠</p></AdminShell>);

    await userEvent.setup().click(screen.getByRole("button", { name: "로그아웃" }));

    expect(navigation.replace).toHaveBeenCalledWith("/login?logout=failed");
  });
});
