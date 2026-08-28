import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/shared/query/query-provider";

import { PostListScreen } from "./post-list-screen";

const { routerPush } = vi.hoisted(() => ({
  routerPush: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts",
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => new URLSearchParams("status=PENDING&page=1"),
}));

describe("PostListScreen", () => {
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
  });

  it("keeps filters and page in every detail link", async () => {
    render(
      <QueryProvider>
        <PostListScreen />
      </QueryProvider>,
    );

    const links = await screen.findAllByRole("link", { name: /한강의 노을/ });
    expect(links[0]).toHaveAttribute(
      "href",
      expect.stringContaining(
        "returnTo=%2Fposts%3Fstatus%3DPENDING%26page%3D1",
      ),
    );
  });

  it("shows only administrator-reviewable statuses", async () => {
    render(
      <QueryProvider>
        <PostListScreen />
      </QueryProvider>,
    );

    const statusFilter = await screen.findByRole("combobox", {
      name: "검수 상태",
    });
    expect(statusFilter).toHaveValue("PENDING");
    expect(
      screen.queryByRole("option", { name: "이미지 처리 중" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("이미지 처리 중")).not.toBeInTheDocument();
  });

  it("lets administrators select topics and authors without typing UUIDs", async () => {
    const user = userEvent.setup();
    render(
      <QueryProvider>
        <PostListScreen />
      </QueryProvider>,
    );

    const topic = await screen.findByRole("combobox", { name: "주제" });
    const author = screen.getByRole("combobox", { name: "작성자" });
    await screen.findByRole("option", { name: /여름의 한 장면/ });
    await user.selectOptions(topic, "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    await user.selectOptions(author, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    await user.click(screen.getByRole("button", { name: "필터 적용" }));

    expect(screen.getByRole("option", { name: /여름의 한 장면/ })).toBeVisible();
    expect(
      screen.getByRole("option", { name: "creator@example.com" }),
    ).toBeVisible();
    expect(screen.queryByPlaceholderText("UUID")).not.toBeInTheDocument();
    expect(routerPush).toHaveBeenCalledWith(
      expect.stringContaining(
        "topicId=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
      ),
    );
    expect(routerPush).toHaveBeenCalledWith(
      expect.stringContaining(
        "userId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      ),
    );
  });
});
