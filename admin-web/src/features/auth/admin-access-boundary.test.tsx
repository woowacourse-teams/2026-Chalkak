import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminAccessBoundary } from "./admin-access-boundary";

const mocks = vi.hoisted(() => ({
  pathname: "/posts/11111111-1111-4111-8111-111111111111",
  params: new URLSearchParams("returnTo=%2Fposts%3Fstatus%3DPENDING"),
  replace: vi.fn(),
  session: { status: "anonymous", error: null, refresh: vi.fn(), logout: vi.fn() },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => mocks.pathname,
  useSearchParams: () => mocks.params,
  useRouter: () => ({ replace: mocks.replace }),
}));
vi.mock("./admin-session-provider", () => ({ useAdminSession: () => mocks.session }));
vi.mock("@/shared/layout/admin-shell", () => ({ AdminShell: ({ children }: { children: React.ReactNode }) => <div data-testid="shell">{children}</div> }));

beforeEach(() => {
  mocks.replace.mockClear();
  mocks.pathname = "/posts/11111111-1111-4111-8111-111111111111";
  mocks.session.status = "anonymous";
});

describe("admin screen protection", () => {
  it("hides protected content and preserves the original deep link", async () => {
    render(<AdminAccessBoundary>private screen</AdminAccessBoundary>);
    expect(screen.queryByText("private screen")).not.toBeInTheDocument();
    const returnTo = mocks.pathname + "?" + mocks.params.toString();
    await waitFor(() => expect(mocks.replace).toHaveBeenCalledWith("/login?" + new URLSearchParams({ returnTo })));
  });

  it("renders the public login page without the administrator shell", () => {
    mocks.pathname = "/login";
    render(<AdminAccessBoundary>login form</AdminAccessBoundary>);
    expect(screen.getByText("login form")).toBeInTheDocument();
    expect(screen.queryByTestId("shell")).not.toBeInTheDocument();
    expect(mocks.replace).not.toHaveBeenCalled();
  });

  it("shows protected content only after authentication", () => {
    mocks.session.status = "authenticated";
    render(<AdminAccessBoundary>private screen</AdminAccessBoundary>);
    expect(screen.getByTestId("shell")).toHaveTextContent("private screen");
  });

  it("shows a permissions error rather than a login redirect for 403", () => {
    mocks.session.status = "forbidden";
    render(<AdminAccessBoundary>private screen</AdminAccessBoundary>);
    expect(screen.getByRole("heading", { name: "관리자 권한이 필요합니다" })).toBeInTheDocument();
    expect(screen.queryByText("private screen")).not.toBeInTheDocument();
    expect(mocks.replace).not.toHaveBeenCalled();
  });
});
