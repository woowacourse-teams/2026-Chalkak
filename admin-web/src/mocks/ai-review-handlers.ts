import { http, HttpResponse } from "msw";
import { aiReviewStatuses, PAGE_SIZE } from "@/features/ai-reviews/model/ai-review";
import { isUuid } from "@/shared/api/uuid";
import type { AdminAiReviewDetail, AdminAiReviewSummary, AiReviewStatus } from "@/shared/api/contracts";
import { postIds, userIds } from "./fixtures";

const promptVersions = {
  v2: { promptVersionId: "cccccccc-cccc-4ccc-8ccc-cccccccccc02", version: 2, model: "gpt-6.1-sol" },
  v1: { promptVersionId: "cccccccc-cccc-4ccc-8ccc-cccccccccc01", version: 1, model: "gpt-6-luna" },
};
const images = [
  "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
  "https://images.unsplash.com/photo-1469474968028-56623f02e42e",
  "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05",
];
const authors = {
  active: { userId: userIds.active, email: "creator@example.com", status: "ACTIVE", deletedAt: null },
  banned: { userId: userIds.banned, email: "banned@example.com", status: "BANNED", deletedAt: null },
  withdrawn: { userId: userIds.withdrawn, email: null, status: "WITHDRAWN", deletedAt: "2026-10-18T03:00:00Z" },
} as const;
const feedback = {
  summary: "건물의 크기와 높이 차이가 잘 비교되는 구도예요",
  strength: "아래쪽 계단과 겹겹이 쌓인 집들, 위쪽 아파트가 한 화면에 같이 담겼어요.",
  weakness: "맨 아래 담장과 검은 옥상이 골목의 흐름을 끊어요.",
  suggestion: "왼쪽 계단 입구와 붉은 도로는 남기면서 맨 아래 담장이 덜 나오게 담아보세요.",
};
const baseTime = Date.parse("2026-10-25T09:00:00Z");
const hex = (n: number) => n.toString(16).padStart(12, "0");

function build(index: number, overrides: Partial<AdminAiReviewDetail> = {}): AdminAiReviewDetail {
  const status = overrides.status ?? "COMPLETED";
  const completed = status === "COMPLETED";
  const requestedAt = new Date(baseTime - index * 60_000).toISOString();
  const score = Math.round((6 + (index % 8) * 0.5) * 10) / 10;
  return {
    aiReviewId: `a1000000-0000-4000-8000-${hex(index)}`,
    status, errorCode: null, requestCount: 1,
    overallScore: completed ? score : null,
    promptVersion: promptVersions.v2,
    post: {
      postId: `b2000000-0000-4000-8000-${hex(index)}`, thumbnailUrl: `${images[index % images.length]}?w=640`,
      imageUrl: images[index % images.length], moderationStatus: "APPROVED", deletedAt: null,
    },
    author: authors.active,
    usage: completed
      ? { inputTokens: 1450, cachedInputTokens: 1024, outputTokens: 620, reasoningTokens: 410 }
      : { inputTokens: null, cachedInputTokens: null, outputTokens: null, reasoningTokens: null },
    latencyMs: completed ? 4200 : null,
    requestedAt, lastRequestedAt: requestedAt,
    startedAt: status === "QUEUED" ? null : requestedAt,
    completedAt: completed ? new Date(baseTime - index * 60_000 + 4_500).toISOString() : null,
    visibleToAuthor: true, attempt: status === "QUEUED" ? 0 : 1, openaiResponseId: completed ? `resp_mock_${index}` : null,
    result: completed ? {
      overallScore: score,
      criteria: [{ key: "COMPOSITION", label: "구도", score }],
      distinctivePerspective: index % 2 === 0, feedback,
    } : null,
    output: completed ? { compositionScore: score, themeExpressionScore: 7.4, feedback } : null,
    ...overrides,
  };
}

function buildFixtures(): AdminAiReviewDetail[] {
  const rows: AdminAiReviewDetail[] = [
    build(0, { post: { ...build(0).post, postId: postIds.approved }, requestCount: 2 }),
    build(1, {
      status: "QUEUED", errorCode: "TIMEOUT", requestCount: 2, attempt: 1,
      usage: { inputTokens: 1450, cachedInputTokens: null, outputTokens: null, reasoningTokens: null }, latencyMs: 60_000,
    }),
    build(2, { status: "PROCESSING" }),
    build(3, {
      status: "FAILED", errorCode: "INVALID_OUTPUT", attempt: 3, openaiResponseId: "resp_mock_failed",
      usage: { inputTokens: 1450, cachedInputTokens: null, outputTokens: 8000, reasoningTokens: 7600 }, latencyMs: 31_800,
    }),
    build(4, { post: { ...build(4).post, deletedAt: "2026-10-24T08:00:00Z" }, visibleToAuthor: false }),
    build(5, { post: { ...build(5).post, moderationStatus: "REJECTED" }, visibleToAuthor: false }),
    build(6, { author: authors.withdrawn, visibleToAuthor: false }),
    build(7, { author: authors.banned }),
    build(8, {
      promptVersion: promptVersions.v1,
      usage: { inputTokens: null, cachedInputTokens: null, outputTokens: null, reasoningTokens: null }, latencyMs: null,
    }),
  ];
  for (let index = rows.length; index < PAGE_SIZE + 5; index += 1) {
    rows.push(build(index, { promptVersion: index % 3 === 0 ? promptVersions.v1 : promptVersions.v2 }));
  }
  return rows;
}

export const aiReviewFixtures = buildFixtures();
const path = "*/api/v1/admin/ai-reviews";
const error = (message: string, status = 400) => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });
const invalidQuery = () => error("조회 조건이 올바르지 않습니다.");

function summary(detail: AdminAiReviewDetail): AdminAiReviewSummary {
  const { postId, thumbnailUrl, moderationStatus, deletedAt } = detail.post;
  return {
    aiReviewId: detail.aiReviewId, status: detail.status, errorCode: detail.errorCode, requestCount: detail.requestCount,
    overallScore: detail.overallScore, promptVersion: detail.promptVersion,
    post: { postId, thumbnailUrl, moderationStatus, deletedAt },
    author: detail.author, usage: detail.usage, latencyMs: detail.latencyMs, requestedAt: detail.requestedAt,
    lastRequestedAt: detail.lastRequestedAt, completedAt: detail.completedAt, visibleToAuthor: detail.visibleToAuthor,
  };
}

function integerParam(value: string | null, fallback: number) {
  if (value === null) return fallback;
  return /^\d+$/.test(value) ? Number(value) : NaN;
}

export const aiReviewHandlers = [
  http.get(path, ({ request }) => {
    const params = new URL(request.url).searchParams;
    const status = params.get("status");
    const promptVersionId = params.get("promptVersionId");
    const page = integerParam(params.get("page"), 1);
    const pageSize = integerParam(params.get("pageSize"), 20);
    if ((status !== null && !aiReviewStatuses.includes(status as AiReviewStatus)) ||
      (promptVersionId !== null && !isUuid(promptVersionId)) ||
      !(page >= 1) || !(pageSize >= 1 && pageSize <= 100)) return invalidQuery();
    const rows = aiReviewFixtures
      .filter((row) => !status || row.status === status)
      .filter((row) => !promptVersionId || row.promptVersion.promptVersionId.toLowerCase() === promptVersionId.toLowerCase())
      .sort((left, right) => right.lastRequestedAt.localeCompare(left.lastRequestedAt) || right.aiReviewId.localeCompare(left.aiReviewId));
    const start = (page - 1) * pageSize;
    return HttpResponse.json({
      currentPage: page, pageSize, hasNext: start + pageSize < rows.length,
      aiReviews: rows.slice(start, start + pageSize).map(summary),
    });
  }),
  http.get(path + "/:id", ({ params }) => {
    const id = String(params.id);
    if (!isUuid(id)) return invalidQuery();
    const review = aiReviewFixtures.find((row) => row.aiReviewId === id);
    return review ? HttpResponse.json(review) : error("AI 리뷰를 찾을 수 없습니다.", 404);
  }),
];
