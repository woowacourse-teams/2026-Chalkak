"use client";
import { useQuery } from "@tanstack/react-query";
import { queryKeys } from "@/shared/query/query-client";
import type { AiReviewQuery } from "../model/ai-review";
import { fetchAiReview, fetchAiReviews } from "./ai-review-api";

export function useAiReviews(query: AiReviewQuery) {
  return useQuery({ queryKey: [...queryKeys.aiReviews, "list", query], queryFn: ({ signal }) => fetchAiReviews(query, signal) });
}
export function useAiReview(id: string | null) {
  return useQuery({
    queryKey: [...queryKeys.aiReviews, "detail", id], enabled: id !== null,
    queryFn: ({ signal }) => fetchAiReview(id!, signal),
  });
}
