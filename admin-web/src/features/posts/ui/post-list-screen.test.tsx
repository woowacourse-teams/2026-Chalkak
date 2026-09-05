import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QueryProvider } from "@/shared/query/query-provider";

import { PostListScreen } from "./post-list-screen";

const { routerPush, search } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  search: { value: "status=PENDING&page=1" },
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/posts",
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => new URLSearchParams(search.value),
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
    search.value = "status=PENDING&page=1";
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

    const statuses = await screen.findByRole("navigation", { name: "검수 상태" });
    expect(statuses.querySelector('[aria-current="page"]')).toHaveTextContent("검수 대기");
    expect(screen.getByRole("link", { name: "승인" })).toHaveAttribute("href", "/posts?status=APPROVED&page=1");
    expect(screen.getByRole("link", { name: "거절" })).toHaveAttribute("href", "/posts?status=REJECTED&page=1");
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

    await user.click(screen.getByText("필터"));
    const topic = await screen.findByRole("combobox", { name: "주제" });
    const author = screen.getByRole("combobox", { name: "작성자" });
    await screen.findByRole("option", { name: /오늘의 빛/ });
    await user.selectOptions(topic, "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc");
    await user.selectOptions(author, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    await user.click(screen.getByRole("button", { name: "필터 적용" }));

    expect(screen.getByRole("option", { name: /오늘의 빛/ })).toBeVisible();
    expect(
      screen.getByRole("option", { name: "creator@example.com" }),
    ).toBeVisible();
    expect(screen.queryByPlaceholderText("UUID")).not.toBeInTheDocument();
    expect(routerPush).toHaveBeenCalledWith(
      expect.stringContaining(
        "topicId=bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc",
      ),
    );
    expect(routerPush).toHaveBeenCalledWith(
      expect.stringContaining(
        "userId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      ),
    );
  });

  it("preserves a zero-result user filter and the source link", async () => {
    search.value = "status=REJECTED&userId=abababab-abab-4bab-8bab-abababababab&returnTo=%2Fusers%3Fstatus%3DBANNED";
    const user = userEvent.setup();
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    expect(await screen.findByText("조건에 맞는 게시물이 없습니다")).toBeInTheDocument();
    await user.click(screen.getByText("필터 · 적용 중"));
    expect(await screen.findByRole("option", { name: "paused@example.com" })).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "작성자" })).toHaveValue("abababab-abab-4bab-8bab-abababababab");
    expect(screen.getByRole("link", { name: "← 사용자·주제로 돌아가기" })).toHaveAttribute("href", "/users?status=BANNED");
    await user.click(screen.getByRole("button", { name: "필터 적용" }));
    expect(routerPush).toHaveBeenCalledWith(expect.stringContaining("userId=abababab-abab-4bab-8bab-abababababab"));
  });
});
