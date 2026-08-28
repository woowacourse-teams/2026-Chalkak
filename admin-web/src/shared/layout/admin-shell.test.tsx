import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AdminShell } from "./admin-shell";

const { pathnameState } = vi.hoisted(() => ({
  pathnameState: { value: "/posts" },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => pathnameState.value,
}));

describe("AdminShell", () => {
  beforeEach(() => {
    pathnameState.value = "/posts";
  });

  it("marks the current menu and shows the development environment", () => {
    render(
      <AdminShell>
        <p>게시물 콘텐츠</p>
      </AdminShell>,
    );

    expect(screen.getByRole("link", { name: /게시물/ })).toHaveAttribute(
      "aria-current",
      "page",
    );
    expect(screen.getByText("개발 관리자")).toBeInTheDocument();
    expect(screen.getByText("DEV")).toBeInTheDocument();
  });

  it("opens the mobile drawer and restores focus after Escape", async () => {
    const user = userEvent.setup();
    render(
      <AdminShell>
        <p>게시물 콘텐츠</p>
      </AdminShell>,
    );
    const menuButton = screen.getByRole("button", {
      name: "관리자 메뉴 열기",
    });

    await user.click(menuButton);

    expect(
      screen.getByRole("complementary", { name: "모바일 관리자 메뉴" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: /대시보드/ })[1]).toHaveFocus();

    await user.keyboard("{Escape}");

    expect(
      screen.queryByRole("complementary", { name: "모바일 관리자 메뉴" }),
    ).not.toBeInTheDocument();
    expect(menuButton).toHaveFocus();
  });

  it("does not persist a development administrator identifier", () => {
    const storageSpy = vi.spyOn(Storage.prototype, "setItem");

    render(
      <AdminShell>
        <p>게시물 콘텐츠</p>
      </AdminShell>,
    );

    expect(storageSpy).not.toHaveBeenCalled();
    storageSpy.mockRestore();
  });
});
