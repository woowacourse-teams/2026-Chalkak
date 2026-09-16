import type { AdminFeedbackListResponse } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";
import { serializeQuery } from "@/shared/api/query-string";

export type AdminFeedbackSort = "createdAtDesc" | "createdAtAsc";

export interface AdminFeedbackFilters {
  sort: AdminFeedbackSort;
  page: number;
  pageSize: number;
}

export function fetchAdminFeedbacks(filters: AdminFeedbackFilters, signal?: AbortSignal) {
  return getApiClient().request<AdminFeedbackListResponse>(
    "/feedbacks" +
      serializeQuery({
        sort: filters.sort,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    { signal },
  );
}
