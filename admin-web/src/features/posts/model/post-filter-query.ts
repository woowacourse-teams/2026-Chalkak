import type { ModerationStatus } from "@/shared/api/contracts";

import type {
  AdminPostFilters,
  AdminPostInstagramConsentFilter,
  AdminPostSort,
} from "../api/post-api";

const moderationStatuses = new Set<ModerationStatus>([
  "PENDING",
  "APPROVED",
  "REJECTED",
]);

const instagramConsents = new Set<AdminPostInstagramConsentFilter>([
  "agreed",
  "notAgreed",
  "revoked",
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
  const rawConsent = params.get("instagramConsent") as AdminPostInstagramConsentFilter | null;
  const topicId = optional(params.get("topicId"));

  return {
    status:
      rawStatus && moderationStatuses.has(rawStatus) ? rawStatus : "PENDING",
    topicId,
    userId: optional(params.get("userId")),
    instagramConsent:
      rawConsent && instagramConsents.has(rawConsent) ? rawConsent : undefined,
    instagramId: optional(params.get("instagramId")),
    // The server rejects popularity sort without a topic.
    sort:
      rawSort === "createdAtAsc" ||
      rawSort === "createdAtDesc" ||
      (rawSort === "likeCountDesc" && topicId)
        ? rawSort
        : "createdAtDesc",
    page: positiveInteger(params.get("page"), 1),
    pageSize: Math.min(100, positiveInteger(params.get("pageSize"), 20)),
  };
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
