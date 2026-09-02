import type { ModerationStatus } from "@/shared/api/contracts";

import type { AdminPostFilters, AdminPostSort } from "../api/post-api";

const moderationStatuses = new Set<ModerationStatus>([
  "PENDING",
  "APPROVED",
  "REJECTED",
]);

function positiveInteger(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function optional(value: string | null) {
  return value?.trim() || undefined;
}

export function readAdminPostFilters(
  params: Pick<URLSearchParams, "get">,
): AdminPostFilters {
  const rawStatus = params.get("status") as ModerationStatus | null;
  const rawSort = params.get("sort") as AdminPostSort | null;

  return {
    status:
      rawStatus && moderationStatuses.has(rawStatus) ? rawStatus : "PENDING",
    topicId: optional(params.get("topicId")),
    topicDate: optional(params.get("topicDate")),
    userId: optional(params.get("userId")),
    createdAtFrom: optional(params.get("createdAtFrom")),
    createdAtTo: optional(params.get("createdAtTo")),
    sort:
      rawSort === "createdAtAsc" || rawSort === "createdAtDesc"
        ? rawSort
        : "createdAtDesc",
    page: positiveInteger(params.get("page"), 1),
    pageSize: Math.min(100, positiveInteger(params.get("pageSize"), 20)),
  };
}

export function dateStartInstant(value: string) {
  return value ? new Date(value + "T00:00:00+09:00").toISOString() : undefined;
}

export function dateEndInstant(value: string) {
  return value ? new Date(value + "T23:59:59.999+09:00").toISOString() : undefined;
}

export function instantDate(value?: string) {
  if (!value) return "";
  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? "" : new Date(timestamp + 9 * 60 * 60 * 1000).toISOString().slice(0, 10);
}

export function withQueryPatch(
  current: Pick<URLSearchParams, "toString">,
  patch: Readonly<Record<string, string | number | undefined>>,
) {
  const next = new URLSearchParams(current.toString());
  for (const [key, value] of Object.entries(patch)) {
    if (value === undefined || value === "") {
      next.delete(key);
    } else {
      next.set(key, String(value));
    }
  }
  const serialized = next.toString();
  return serialized ? "?" + serialized : "";
}
