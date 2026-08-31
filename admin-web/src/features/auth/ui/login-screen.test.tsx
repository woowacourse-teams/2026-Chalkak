import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/shared/api/errors";

import { LoginScreen } from "./login-screen";

const { session, navigation, login, logout } = vi.hoisted(() => {
  const login = vi.fn<(username: string, password: string) => Promise<void>>();
  const logout = vi.fn<() => Promise<void>>();
  return {
    session: {
      status: "anonymous" as "loading" | "logging-out" | "authenticated" | "anonymous" | "error" | "forbidden",
      error: null as string | null,
      isMock: false,
      login,
      logout,
    },
    navigation: { params: "", replace: vi.fn() },
    login,
    logout,
  };
});

vi.mock("@/features/auth/admin-session-provider", () => ({
  useAdminSession: () => session,
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: navigation.replace }),
  useSearchParams: () => new URLSearchParams(navigation.params),
}));

describe("LoginScreen", () => {
  beforeEach(() => {
    session.status = "anonymous";
    session.error = null;
    session.isMock = false;
    navigation.params = "";
    navigation.replace.mockReset();
    login.mockReset().mockResolvedValue(undefined);
    logout.mockReset().mockResolvedValue(undefined);
  });

  it("logs in with the entered credentials and returns to an internal screen", async () => {
    const user = userEvent.setup();
    navigation.params = new URLSearchParams({ returnTo: "/posts?status=PENDING&page=2" }).toString();
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");
    render(<LoginScreen />);

    await user.type(screen.getByLabelText("아이디"), "operator");
    await user.type(screen.getByLabelText("비밀번호", { exact: true }), "test-password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(login).toHaveBeenCalledWith("operator", "test-password");
    expect(navigation.replace).toHaveBeenCalledWith("/posts?status=PENDING&page=2");
    expect(storageSpy).not.toHaveBeenCalled();
    expect(screen.queryByRole("button", { name: "데모 계정으로 둘러보기" })).not.toBeInTheDocument();
    storageSpy.mockRestore();
  });

  it("does not redirect to an external return URL", async () => {
    session.isMock = true;
    navigation.params = new URLSearchParams({ returnTo: "https://other.example" }).toString();
    render(<LoginScreen />);

    await userEvent.setup().click(screen.getByRole("button", { name: "데모 계정으로 둘러보기" }));

    expect(login).toHaveBeenCalledWith("demo-admin", "demo-only");
    expect(navigation.replace).toHaveBeenCalledWith("/");
  });

  it("shows the password only when explicitly requested", async () => {
    const user = userEvent.setup();
    render(<LoginScreen />);
    const password = screen.getByLabelText("비밀번호", { exact: true });

    expect(screen.getByLabelText("아이디")).toHaveAttribute("maxlength", "100");
    expect(password).toHaveAttribute("maxlength", "200");
    expect(password).toHaveAttribute("type", "password");
    await user.click(screen.getByRole("button", { name: "비밀번호 보기" }));
    expect(password).toHaveAttribute("type", "text");
    expect(screen.getByRole("button", { name: "비밀번호 숨기기" })).toHaveAttribute("aria-pressed", "true");
    await user.click(screen.getByRole("button", { name: "비밀번호 숨기기" }));
    expect(password).toHaveAttribute("type", "password");
  });

  it("announces credential errors and allows another attempt", async () => {
    const user = userEvent.setup();
    login.mockRejectedValueOnce(new ApiError({ kind: "api", status: 401, message: "아이디 또는 비밀번호가 올바르지 않습니다." }));
    render(<LoginScreen />);

    await user.type(screen.getByLabelText("아이디"), "operator");
    await user.type(screen.getByLabelText("비밀번호", { exact: true }), "wrong-password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(screen.getByRole("alert")).toHaveTextContent("아이디 또는 비밀번호가 올바르지 않습니다.");
    expect(screen.getByRole("alert")).toHaveFocus();
    expect(navigation.replace).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled();
  });

  it("prevents duplicate submissions while a login is pending", async () => {
    const user = userEvent.setup();
    let finishLogin!: () => void;
    login.mockImplementationOnce(() => new Promise<void>((resolve) => { finishLogin = resolve; }));
    session.isMock = true;
    render(<LoginScreen />);

    await user.type(screen.getByLabelText("아이디"), "operator");
    await user.type(screen.getByLabelText("비밀번호", { exact: true }), "test-password");
    await user.click(screen.getByRole("button", { name: "로그인" }));

    expect(screen.getByRole("button", { name: "로그인 중…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "데모 계정으로 둘러보기" })).toBeDisabled();
    expect(screen.getByLabelText("아이디")).toBeDisabled();
    await act(async () => { finishLogin(); });
    expect(login).toHaveBeenCalledTimes(1);
  });

  it("waits for the initial session check before accepting credentials", () => {
    session.status = "loading";
    render(<LoginScreen />);

    expect(screen.getByRole("status")).toHaveTextContent("로그인 상태를 확인하고 있습니다.");
    expect(screen.getByLabelText("아이디")).toBeDisabled();
    expect(screen.getByRole("button", { name: "로그인" })).toBeDisabled();
  });

  it("redirects an already authenticated administrator", async () => {
    session.status = "authenticated";
    navigation.params = "returnTo=%2Ftopics";
    render(<LoginScreen />);

    await waitFor(() => expect(navigation.replace).toHaveBeenCalledWith("/topics"));
    expect(screen.queryByRole("form")).not.toBeInTheDocument();
  });

  it("does not accept a new login while logout is still completing", () => {
    session.status = "logging-out";
    render(<LoginScreen />);

    expect(screen.getByLabelText("아이디")).toBeDisabled();
    expect(screen.getByRole("button", { name: "로그인" })).toBeDisabled();
  });

  it("shows a session error without preventing a fresh login", () => {
    session.status = "forbidden";
    render(<LoginScreen />);

    expect(screen.getByRole("alert")).toHaveTextContent("관리자 권한이 필요합니다.");
    expect(screen.getByLabelText("아이디")).toHaveAttribute("aria-describedby", "login-error");
    expect(screen.getByRole("button", { name: "로그인" })).toBeEnabled();
  });

  it("keeps a failed logout warning visible even if the cookie still authenticates", async () => {
    session.status = "authenticated";
    navigation.params = "logout=failed";
    render(<LoginScreen />);

    expect(screen.getByRole("alert")).toHaveTextContent("로그아웃 확인에 실패했습니다.");
    expect(navigation.replace).not.toHaveBeenCalled();
    expect(screen.queryByRole("form")).not.toBeInTheDocument();

    await userEvent.setup().click(screen.getByRole("button", { name: "로그아웃 다시 시도" }));

    expect(logout).toHaveBeenCalledOnce();
    expect(navigation.replace).toHaveBeenCalledWith("/login");
  });

  it("retains the warning when logout retry fails", async () => {
    logout.mockRejectedValueOnce(new Error("network"));
    navigation.params = "logout=failed";
    render(<LoginScreen />);

    await userEvent.setup().click(screen.getByRole("button", { name: "로그아웃 다시 시도" }));

    expect(screen.getByRole("alert")).toHaveTextContent("연결 상태를 확인하고 다시 시도해 주세요.");
    expect(screen.getByRole("button", { name: "로그아웃 다시 시도" })).toBeEnabled();
    expect(navigation.replace).not.toHaveBeenCalled();
  });
});
