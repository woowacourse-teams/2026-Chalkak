import type { UserStatus } from "@/shared/api/contracts";

import type { AdminUserFilters, AdminUserSort } from "../api/user-api";
import { withQueryPatch } from "@/features/posts/model/post-filter-query";

const statuses = new Set<UserStatus>(["ACTIVE", "BANNED", "WITHDRAWN"]);

function positive(value: string | null, fallback: number) {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export function readAdminUserFilters(
  params: Pick<URLSearchParams, "get">,
): AdminUserFilters {
  const status = params.get("status") as UserStatus | null;
  const sort = params.get("sort") as AdminUserSort | null;
  return {
    status: status && statuses.has(status) ? status : "ACTIVE",
    email: params.get("email")?.trim() || undefined,
    sort: sort === "createdAtAsc" ? sort : "createdAtDesc",
    page: positive(params.get("page"), 1),
    pageSize: Math.min(100, positive(params.get("pageSize"), 20)),
  };
}

export { withQueryPatch };
