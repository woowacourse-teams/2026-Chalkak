import type { AdminFeedbackFilters, AdminFeedbackSort } from "../api/feedback-api";

const sorts = new Set<AdminFeedbackSort>(["createdAtDesc", "createdAtAsc"]);

function positive(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export function readAdminFeedbackFilters(
  params: Pick<URLSearchParams, "get">,
): AdminFeedbackFilters {
  const sort = params.get("sort") as AdminFeedbackSort | null;
  return {
    sort: sort && sorts.has(sort) ? sort : "createdAtDesc",
    page: positive(params.get("page"), 1),
    pageSize: Math.min(100, positive(params.get("pageSize"), 20)),
  };
}
