import type { AdminFeedbackDetail, AdminFeedbackListResponse } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";
import { serializeQuery } from "@/shared/api/query-string";

export type AdminFeedbackSort = "createdAtDesc" | "createdAtAsc";
export type AdminFeedbackInquiryStatus = "ALL" | "NEEDS_ANSWER" | "ANSWERED" | "INQUIRY_DELETED";

export interface AdminFeedbackFilters {
  userId?: string;
  inquiryStatus?: AdminFeedbackInquiryStatus;
  bannedAuthorOnly?: boolean;
  sort: AdminFeedbackSort;
  page: number;
  pageSize: number;
}

export function fetchAdminFeedbacks(filters: AdminFeedbackFilters, signal?: AbortSignal) {
  return getApiClient().request<AdminFeedbackListResponse>(
    "/feedbacks" +
      serializeQuery({
        userId: filters.userId,
        inquiryStatus: filters.inquiryStatus === "ALL" ? undefined : filters.inquiryStatus,
        bannedAuthorOnly: filters.bannedAuthorOnly ? true : undefined,
        sort: filters.sort,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    { signal },
  );
}

function feedbackPath(feedbackId: string) {
  return "/feedbacks/" + encodeURIComponent(feedbackId);
}

export function fetchAdminFeedback(feedbackId: string, signal?: AbortSignal) {
  return getApiClient().request<AdminFeedbackDetail>(feedbackPath(feedbackId), { signal });
}

export function registerAdminFeedbackAnswer(feedbackId: string, answerContent: string, sendPush?: boolean) {
  return getApiClient().request<void>(feedbackPath(feedbackId) + "/answer", {
    method: "POST",
    body: { answerContent, sendPush },
  });
}

export function deleteAdminFeedbackAnswer(feedbackId: string, answerVersion: string) {
  return getApiClient().request<void>(
    feedbackPath(feedbackId) + "/answer" + serializeQuery({ answerVersion }),
    { method: "DELETE" },
  );
}

export function deleteAdminFeedback(feedbackId: string) {
  return getApiClient().request<void>(feedbackPath(feedbackId), { method: "DELETE" });
}
