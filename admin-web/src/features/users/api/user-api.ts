import type {
  AdminUserDetailResponse,
  AdminUserListResponse,
  AdminUserStatusResponse,
  UserStatus,
} from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";
import { serializeQuery } from "@/shared/api/query-string";

export type AdminUserSort = "createdAtDesc" | "createdAtAsc";

export interface AdminUserFilters {
  status?: UserStatus;
  email?: string;
  sort: AdminUserSort;
  page: number;
  pageSize: number;
}

export function fetchAdminUsers(filters: AdminUserFilters, signal?: AbortSignal) {
  return getApiClient().request<AdminUserListResponse>(
    "/users" +
      serializeQuery({
        status: filters.status,
        email: filters.email,
        sort: filters.sort,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    { signal },
  );
}

export function fetchAdminUser(userId: string, signal?: AbortSignal) {
  return getApiClient().request<AdminUserDetailResponse>("/users/" + userId, {
    signal,
  });
}

export function updateAdminUserStatus(
  userId: string,
  status: "ACTIVE" | "BANNED",
  reason: string,
) {
  return getApiClient().request<AdminUserStatusResponse>(
    "/users/" + userId + "/status",
    { method: "PATCH", body: { status, reason } },
  );
}
