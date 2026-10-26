import { getApiClient } from "@/shared/api/client";
import type { AdminAiReviewDetail, AdminAiReviewListResponse } from "@/shared/api/contracts";
import { buildAiReviewApiPath, type AiReviewQuery } from "../model/ai-review";

export function fetchAiReviews(query: AiReviewQuery, signal?: AbortSignal) {
  return getApiClient().request<AdminAiReviewListResponse>(buildAiReviewApiPath(query), { signal });
}
export function fetchAiReview(id: string, signal?: AbortSignal) {
  return getApiClient().request<AdminAiReviewDetail>(`/ai-reviews/${encodeURIComponent(id)}`, { signal });
}
