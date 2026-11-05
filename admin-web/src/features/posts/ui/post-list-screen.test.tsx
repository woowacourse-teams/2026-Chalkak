import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { server } from "@/mocks/server";
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
    expect(within(statuses).getByRole("link", { name: "전체 처리 이력 ↗" })).toHaveAttribute("href", "/audit-logs");
    expect(within(statuses).getAllByRole("link").slice(-2).map((link) => link.textContent)).toEqual(["거절", "전체 처리 이력 ↗"]);
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

  it("shows Instagram consent and ID in each row", async () => {
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    const row = (await screen.findAllByRole("row")).find((candidate) => within(candidate).queryByText("한강의 노을"));
    expect(row).toBeDefined();
    expect(within(row as HTMLElement).getByText("인스타 동의")).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText("@chalkak_creator")).toBeInTheDocument();
  });

  it("marks posts without consent and omits a missing ID", async () => {
    search.value = "status=REJECTED&page=1";
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    const row = (await screen.findAllByRole("row")).find((candidate) => within(candidate).queryByText("흐린 골목"));
    expect(within(row as HTMLElement).getByText("인스타 미동의")).toBeInTheDocument();
    expect(within(row as HTMLElement).queryByText(/^@/)).not.toBeInTheDocument();
  });

  it("applies Instagram consent and ID filters to the URL", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    await user.click(screen.getByText("필터"));
    await user.selectOptions(screen.getByRole("combobox", { name: "인스타 동의" }), "revoked");
    await user.type(screen.getByRole("textbox", { name: "인스타그램 ID" }), "  @Chalkak ");
    await user.click(screen.getByRole("button", { name: "필터 적용" }));

    const url = routerPush.mock.calls.at(-1)?.[0] as string;
    const params = new URLSearchParams(url.split("?")[1]);
    expect(params.get("instagramConsent")).toBe("revoked");
    expect(params.get("instagramId")).toBe("@Chalkak");
    for (const name of ["topicDate", "createdAtFrom", "createdAtTo"]) {
      expect(params.has(name)).toBe(false);
    }
  });

  it("restores Instagram filters from the URL and marks the filter as applied", async () => {
    search.value = "status=APPROVED&instagramConsent=agreed&instagramId=chalkak&topicDate=2026-08-28";
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    expect(await screen.findByText("필터 · 적용 중")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "인스타 동의" })).toHaveValue("agreed");
    expect(screen.getByRole("textbox", { name: "인스타그램 ID" })).toHaveValue("chalkak");
    expect(screen.queryByLabelText("주제 날짜")).not.toBeInTheDocument();
  });

  it("enables popularity sort only while a topic is selected", async () => {
    const user = userEvent.setup();
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    await user.click(screen.getByText("필터"));
    const topic = screen.getByRole("combobox", { name: "주제" });
    const popular = await screen.findByRole("option", { name: "인기순 (좋아요 많은 순)" });
    expect(popular).toBeDisabled();
    expect(screen.getByText("인기순은 주제를 선택해야 사용할 수 있습니다.")).toBeInTheDocument();

    await screen.findByRole("option", { name: /오늘의 빛/ });
    await user.selectOptions(topic, "bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc");
    expect(popular).toBeEnabled();
    await user.selectOptions(screen.getByRole("combobox", { name: "정렬" }), "likeCountDesc");
    await user.click(screen.getByRole("button", { name: "필터 적용" }));
    expect(routerPush).toHaveBeenLastCalledWith(expect.stringContaining("sort=likeCountDesc"));
  });

  it("resets the sort select to latest order when the topic is cleared under popularity sort", async () => {
    search.value = "status=PENDING&topicId=bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc&sort=likeCountDesc";
    const user = userEvent.setup();
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    await user.click(screen.getByText("필터 · 적용 중"));
    expect(screen.getByText(/순위가 바뀔 수 있습니다/)).toBeInTheDocument();
    const sort = screen.getByRole("combobox", { name: "정렬" });
    expect(sort).toHaveValue("likeCountDesc");
    await user.selectOptions(screen.getByRole("combobox", { name: "주제" }), "");
    expect(sort).toHaveValue("createdAtDesc");
    expect(screen.getByRole("option", { name: "최신 등록순" })).toHaveProperty("selected", true);
    await user.click(screen.getByRole("button", { name: "필터 적용" }));
    const params = new URLSearchParams((routerPush.mock.calls.at(-1)?.[0] as string).split("?")[1]);
    expect(params.has("topicId")).toBe(false);
    expect(params.get("sort")).toBe("createdAtDesc");
  });

  it("never requests popularity sort without a topic, including the filter-option query", async () => {
    search.value = "status=PENDING&topicId=bcbcbcbc-bcbc-4bcb-8bcb-bcbcbcbcbcbc&sort=likeCountDesc";
    const requested: URL[] = [];
    server.use(http.get("*/api/v1/admin/posts", ({ request }) => {
      requested.push(new URL(request.url));
      return HttpResponse.json({ currentPage: 1, pageSize: 20, hasNext: false, posts: [] });
    }));
    render(<QueryProvider><PostListScreen /></QueryProvider>);
    await waitFor(() => expect(requested.length).toBeGreaterThanOrEqual(2));
    const popular = requested.filter((url) => url.searchParams.get("sort") === "likeCountDesc");
    expect(popular.length).toBeGreaterThan(0);
    expect(requested.some((url) => !url.searchParams.has("topicId"))).toBe(true);
    for (const url of popular) {
      expect(url.searchParams.get("topicId")).toBeTruthy();
    }
  });
});
