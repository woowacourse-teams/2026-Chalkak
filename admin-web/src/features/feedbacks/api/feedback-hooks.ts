"use client";

import { useQuery } from "@tanstack/react-query";

import { queryKeys } from "@/shared/query/query-client";

import { fetchAdminFeedbacks, type AdminFeedbackFilters } from "./feedback-api";

export function useAdminFeedbacks(filters: AdminFeedbackFilters) {
  return useQuery({
    queryKey: queryKeys.feedbacks.list(filters),
    queryFn: ({ signal }) => fetchAdminFeedbacks(filters, signal),
  });
}
