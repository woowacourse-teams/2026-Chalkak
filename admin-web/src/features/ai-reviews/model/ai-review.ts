import { userStatusDisplay } from "@/features/users/model/user-display";
import type { AiReviewStatus, UserStatus } from "@/shared/api/contracts";
import { isUuid } from "@/shared/api/uuid";

export const PAGE_SIZE = 20;
export const MAX_PAGE = 107374183;

export const aiReviewStatuses: readonly AiReviewStatus[] = ["QUEUED", "PROCESSING", "COMPLETED", "FAILED"];

export const aiReviewStatusDisplay: Record<AiReviewStatus, { label: string; tone: "neutral" | "info" | "success" | "danger" }> = {
  QUEUED: { label: "대기", tone: "neutral" },
  PROCESSING: { label: "처리 중", tone: "info" },
  COMPLETED: { label: "완료", tone: "success" },
  FAILED: { label: "실패", tone: "danger" },
};

export interface AiReviewQuery {
  status?: AiReviewStatus;
  promptVersionId?: string;
  page: number;
}

// Unknown status and non-UUID prompt version values are dropped so a hand-edited URL never makes the backend answer 400.
export function readAiReviewQuery(params: Pick<URLSearchParams, "get">): AiReviewQuery {
  const status = params.get("status");
  const promptVersionId = params.get("promptVersionId")?.trim();
  const rawPage = Number(params.get("page") ?? 1);
  return {
    status: aiReviewStatuses.find((value) => value === status),
    promptVersionId: promptVersionId && isUuid(promptVersionId) ? promptVersionId : undefined,
    page: Number.isSafeInteger(rawPage) && rawPage > 0 && rawPage <= MAX_PAGE ? rawPage : 1,
  };
}

export function hasAiReviewFilters(query: AiReviewQuery) {
  return query.status !== undefined || query.promptVersionId !== undefined;
}

function toParams(query: AiReviewQuery) {
  const params = new URLSearchParams();
  if (query.status) params.set("status", query.status);
  if (query.promptVersionId) params.set("promptVersionId", query.promptVersionId);
  return params;
}

export function buildAiReviewListHref(query: AiReviewQuery) {
  const params = toParams(query);
  if (query.page > 1) params.set("page", String(query.page));
  const search = params.toString();
  return search ? `/ai-reviews?${search}` : "/ai-reviews";
}

export function buildAiReviewApiPath(query: AiReviewQuery) {
  const params = toParams(query);
  params.set("page", String(query.page));
  params.set("pageSize", String(PAGE_SIZE));
  return `/ai-reviews?${params.toString()}`;
}

// A null count means no call was recorded.
export function formatCount(value: number | null) {
  return value === null ? "-" : value.toLocaleString();
}

export function formatAuthorStatus(status: UserStatus) {
  return `${userStatusDisplay[status].label}(${status})`;
}

// The version options only cover the first page of versions, so a deep-linked id may be missing from them.
export function formatUnlistedVersion(promptVersionId: string) {
  return `버전 ID ${promptVersionId.slice(0, 8)}`;
}
