import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { AiTabs } from "@/features/ai-prompts/ui/ai-tabs";
import { aiReviewFixtures } from "@/mocks/ai-review-handlers";
import { server } from "@/mocks/server";
import { getCurrentNavigation } from "@/shared/navigation/admin-navigation";
import { createQueryClient } from "@/shared/query/query-client";
import { AiReviewDetailScreen } from "./ai-review-detail-screen";
import { AiReviewListScreen } from "./ai-review-list-screen";

const navigation = vi.hoisted(() => ({ push: vi.fn(), search: "", pathname: "/ai-reviews" }));
vi.mock("next/navigation", () => ({
  useRouter: () => navigation, useSearchParams: () => new URLSearchParams(navigation.search), usePathname: () => navigation.pathname,
}));
beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  navigation.push.mockReset(); navigation.search = ""; navigation.pathname = "/ai-reviews";
});
afterEach(() => vi.unstubAllEnvs());
function mount(node: ReactNode) {
  const client = createQueryClient(); client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}
const emptyList = { currentPage: 1, pageSize: 20, hasNext: false, aiReviews: [] };
const v2 = "cccccccc-cccc-4ccc-8ccc-cccccccccc02";

describe("AI review list", () => {
  it("renders columns for a completed review", async () => {
    mount(<AiReviewListScreen />);
    const table = await screen.findByRole("table", { name: "AI 리뷰 목록" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(20);
    const row = rows[0];
    expect(within(row).getByText("creator@example.com")).toBeInTheDocument();
    expect(within(row).getByText("완료")).toBeInTheDocument();
    expect(within(row).getByText(String(aiReviewFixtures[0].overallScore))).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "v2" })).toHaveAttribute("href", `/ai-prompts/${v2}`);
    expect(within(row).getByText("입력 1,450")).toBeInTheDocument();
    expect(within(row).getByText("출력 620")).toBeInTheDocument();
    expect(within(row).getByText("응답 4.2초")).toBeInTheDocument();
    expect(within(row).getByRole("link", { name: "상세 보기" })).toHaveAttribute("href", `/ai-reviews/${aiReviewFixtures[0].aiReviewId}`);
    expect(within(row).getByRole("link", { name: "AI 리뷰 상세 보기(썸네일)" })).toHaveAttribute("href", `/ai-reviews/${aiReviewFixtures[0].aiReviewId}`);
    expect(within(row).queryByText("앱에서 숨김")).not.toBeInTheDocument();
  });

  it("shows error code for a retry-pending review and dashes for missing values", async () => {
    mount(<AiReviewListScreen />);
    const table = await screen.findByRole("table", { name: "AI 리뷰 목록" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(within(rows[1]).getByText("대기")).toBeInTheDocument();
    expect(within(rows[1]).getByText("TIMEOUT")).toBeInTheDocument();
    expect(within(rows[2]).getByText("처리 중")).toBeInTheDocument();
    expect(within(rows[2]).getByText("입력 -")).toBeInTheDocument();
  });

  it("renders a card per review for narrow screens", async () => {
    mount(<AiReviewListScreen />);
    await screen.findByRole("table", { name: "AI 리뷰 목록" });
    const cards = screen.getAllByRole("article");
    expect(cards).toHaveLength(20);
    expect(within(cards[0]).getByText(/creator@example.com/)).toBeInTheDocument();
    expect(within(cards[0]).getByRole("link", { name: "상세 보기" })).toHaveAttribute("href", `/ai-reviews/${aiReviewFixtures[0].aiReviewId}`);
    expect(within(cards[1]).getByText("TIMEOUT")).toBeInTheDocument();
    expect(within(cards[4]).getByText("앱에서 숨김")).toBeInTheDocument();
  });

  it("marks hidden reviews with the raw post and author values", async () => {
    mount(<AiReviewListScreen />);
    const table = await screen.findByRole("table", { name: "AI 리뷰 목록" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(within(rows[4]).getByText("앱에서 숨김")).toBeInTheDocument();
    expect(within(rows[4]).getByText(/게시물 삭제됨/)).toBeInTheDocument();
    expect(within(rows[5]).getByText("게시물 거절(REJECTED)")).toBeInTheDocument();
    expect(within(rows[6]).getByText(/작성자 삭제됨/)).toBeInTheDocument();
    expect(within(rows[7]).getByText("차단(BANNED)", { selector: "small" })).toBeInTheDocument();
    expect(within(rows[7]).queryByText("앱에서 숨김")).not.toBeInTheDocument();
  });

  it("sends URL filters, including a version missing from the options, and resets the page on change", async () => {
    const requests: string[] = [];
    server.use(http.get("*/api/v1/admin/ai-reviews", ({ request }) => { requests.push(new URL(request.url).search); return HttpResponse.json(emptyList); }));
    const unknown = "00000000-0000-4000-8000-000000000000";
    navigation.search = `status=FAILED&promptVersionId=${unknown}&page=2`;
    mount(<AiReviewListScreen />);
    await screen.findByText("조건에 맞는 AI 리뷰가 없습니다");
    expect(requests[0]).toBe(`?status=FAILED&promptVersionId=${unknown}&page=2&pageSize=20`);
    expect(await screen.findByRole("option", { name: "v2 · gpt-6.1-sol" })).toBeInTheDocument();
    expect(screen.getByRole("option", { name: "버전 ID 00000000" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: /알 수 없는/ })).not.toBeInTheDocument();
    expect(screen.getByLabelText("프롬프트 버전")).toHaveValue(unknown);
    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "COMPLETED" } });
    expect(navigation.push).toHaveBeenCalledWith(`/ai-reviews?status=COMPLETED&promptVersionId=${unknown}`);
    fireEvent.change(screen.getByLabelText("프롬프트 버전"), { target: { value: v2 } });
    expect(navigation.push).toHaveBeenLastCalledWith(`/ai-reviews?status=FAILED&promptVersionId=${v2}`);
    fireEvent.change(screen.getByLabelText("프롬프트 버전"), { target: { value: "" } });
    expect(navigation.push).toHaveBeenLastCalledWith("/ai-reviews?status=FAILED");
  });

  it("ignores invalid status and version values from the URL", async () => {
    const requests: string[] = [];
    server.use(http.get("*/api/v1/admin/ai-reviews", ({ request }) => { requests.push(new URL(request.url).search); return HttpResponse.json(emptyList); }));
    navigation.search = "status=DONE&promptVersionId=abc&page=-1";
    mount(<AiReviewListScreen />);
    expect(await screen.findByText("AI 리뷰가 없습니다")).toBeInTheDocument();
    expect(requests[0]).toBe("?page=1&pageSize=20");
  });

  it("paginates by hasNext", async () => {
    mount(<AiReviewListScreen />);
    await screen.findByRole("table", { name: "AI 리뷰 목록" });
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    expect(navigation.push).toHaveBeenCalledWith("/ai-reviews?page=2");
  });

  it("disables next on the last page", async () => {
    navigation.search = "page=2";
    mount(<AiReviewListScreen />);
    await screen.findByRole("table", { name: "AI 리뷰 목록" });
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "이전" }));
    expect(navigation.push).toHaveBeenCalledWith("/ai-reviews");
  });

  it("shows a different empty state without filters", async () => {
    server.use(http.get("*/api/v1/admin/ai-reviews", () => HttpResponse.json(emptyList)));
    mount(<AiReviewListScreen />);
    expect(await screen.findByText("AI 리뷰가 없습니다")).toBeInTheDocument();
    expect(screen.queryByText("조건에 맞는 AI 리뷰가 없습니다")).not.toBeInTheDocument();
  });

  it("shows load errors and retries", async () => {
    server.use(http.get("*/api/v1/admin/ai-reviews", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "권한이 없습니다." }, { status: 403 })));
    mount(<AiReviewListScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    server.resetHandlers();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByRole("table", { name: "AI 리뷰 목록" })).toBeInTheDocument();
  });
});

describe("AI review detail", () => {
  it("shows result, call info, links, and image", async () => {
    const review = aiReviewFixtures[0];
    mount(<AiReviewDetailScreen aiReviewId={review.aiReviewId} />);
    expect(await screen.findByText("AI 리뷰 상세")).toBeInTheDocument();
    expect(screen.getByText(review.result!.feedback.summary)).toBeInTheDocument();
    expect(screen.getByText("구도")).toBeInTheDocument();
    expect(screen.getByText("resp_mock_0")).toBeInTheDocument();
    expect(screen.getByText("1,024")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "게시물 상세" })).toHaveAttribute("href", `/posts/${review.post.postId}`);
    expect(screen.getByRole("link", { name: "작성자 상세" })).toHaveAttribute("href", `/users/${review.author.userId}`);
    expect(screen.getByRole("link", { name: "프롬프트 버전 상세" })).toHaveAttribute("href", `/ai-prompts/${v2}`);
    expect(screen.getByAltText("원본 사진")).toHaveAttribute("src", review.post.imageUrl);
  });

  it("keeps the raw output collapsed until opened", async () => {
    mount(<AiReviewDetailScreen aiReviewId={aiReviewFixtures[0].aiReviewId} />);
    const summary = await screen.findByText("원출력 펼쳐 보기");
    const details = summary.closest("details")!;
    expect(details).not.toHaveAttribute("open");
    fireEvent.click(summary);
    expect(details).toHaveAttribute("open");
    expect(within(details).getByText(/"compositionScore"/)).toBeInTheDocument();
  });

  it("explains missing result and output for a non-completed review", async () => {
    mount(<AiReviewDetailScreen aiReviewId={aiReviewFixtures[1].aiReviewId} />);
    expect(await screen.findByText("AI 리뷰 상세")).toBeInTheDocument();
    expect(screen.queryByText("원출력 펼쳐 보기")).not.toBeInTheDocument();
    expect(screen.getByText("원출력은 완료된 리뷰에만 있습니다.")).toBeInTheDocument();
    expect(screen.getByText("리뷰 결과는 완료된 리뷰에만 있습니다.")).toBeInTheDocument();
    expect(screen.getByText("TIMEOUT")).toBeInTheDocument();
  });

  it("falls back to the thumbnail when the original image is missing", async () => {
    const review = { ...aiReviewFixtures[0], post: { ...aiReviewFixtures[0].post, imageUrl: null } };
    server.use(http.get("*/api/v1/admin/ai-reviews/:id", () => HttpResponse.json(review)));
    mount(<AiReviewDetailScreen aiReviewId={review.aiReviewId} />);
    expect(await screen.findByAltText("원본 사진")).toHaveAttribute("src", review.post.thumbnailUrl);
  });

  it("shows a not-found state for a 404", async () => {
    mount(<AiReviewDetailScreen aiReviewId="00000000-0000-4000-8000-000000000000" />);
    expect(await screen.findByText("AI 리뷰를 찾을 수 없습니다")).toBeInTheDocument();
  });

  it("does not request a non-UUID id", async () => {
    const calls = vi.fn();
    server.use(http.get("*/api/v1/admin/ai-reviews/:id", () => { calls(); return HttpResponse.json({}); }));
    mount(<AiReviewDetailScreen aiReviewId="not-a-uuid" />);
    expect(await screen.findByText("AI 리뷰를 찾을 수 없습니다")).toBeInTheDocument();
    await waitFor(() => expect(calls).not.toHaveBeenCalled());
  });
});

describe("AI tabs and navigation", () => {
  it("marks the current tab by pathname", () => {
    navigation.pathname = "/ai-reviews/abc";
    const { unmount } = render(<AiTabs />);
    expect(screen.getByRole("link", { name: "리뷰" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("link", { name: "프롬프트" })).not.toHaveAttribute("aria-current");
    unmount();
    navigation.pathname = "/ai-prompts/new";
    render(<AiTabs />);
    expect(screen.getByRole("link", { name: "프롬프트" })).toHaveAttribute("aria-current", "page");
  });

  it("keeps the AI bottom item selected on review pages", () => {
    const ai = { href: "/ai-prompts", label: "AI", icon: "sparkle" };
    expect(getCurrentNavigation("/ai-reviews")).toEqual(ai);
    expect(getCurrentNavigation("/ai-reviews/abc")).toEqual(ai);
    expect(getCurrentNavigation("/ai-prompts")).toEqual(ai);
  });
});
