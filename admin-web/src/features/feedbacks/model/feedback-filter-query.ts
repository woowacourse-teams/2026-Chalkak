import type { AdminFeedbackFilters, AdminFeedbackInquiryStatus, AdminFeedbackSort } from "../api/feedback-api";

export const inquiryStatusLabels = {
  ALL: "전체",
  NEEDS_ANSWER: "답변 필요",
  ANSWERED: "답변 완료",
  INQUIRY_DELETED: "삭제된 문의",
} satisfies Record<AdminFeedbackInquiryStatus, string>;

const sorts = new Set<AdminFeedbackSort>(["createdAtDesc", "createdAtAsc"]);

function positive(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export function readAdminFeedbackFilters(
  params: Pick<URLSearchParams, "get">,
): AdminFeedbackFilters {
  const sort = params.get("sort") as AdminFeedbackSort | null;
  const inquiryStatus = params.get("inquiryStatus");
  return {
    inquiryStatus: inquiryStatus && Object.hasOwn(inquiryStatusLabels, inquiryStatus)
      ? inquiryStatus as AdminFeedbackInquiryStatus
      : "ALL",
    bannedAuthorOnly: params.get("bannedAuthorOnly") === "true",
    sort: sort && sorts.has(sort) ? sort : "createdAtDesc",
    page: positive(params.get("page"), 1),
    pageSize: Math.min(100, positive(params.get("pageSize"), 20)),
  };
}
