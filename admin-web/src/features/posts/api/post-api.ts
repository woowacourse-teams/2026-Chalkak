import type {
  AdminPostDetailResponse,
  AdminPostListResponse,
  AdminPostModerationResponse,
  ModerationStatus,
} from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";
import { serializeQuery } from "@/shared/api/query-string";

export type AdminPostSort = "createdAtDesc" | "createdAtAsc";

export interface AdminPostFilters {
  status?: ModerationStatus;
  topicId?: string;
  topicDate?: string;
  userId?: string;
  createdAtFrom?: string;
  createdAtTo?: string;
  sort: AdminPostSort;
  page: number;
  pageSize: number;
}

export async function fetchAdminPosts(
  filters: AdminPostFilters,
  signal?: AbortSignal,
) {
  return getApiClient().request<AdminPostListResponse>(
    "/posts" +
      serializeQuery({
        status: filters.status,
        topicId: filters.topicId,
        topicDate: filters.topicDate,
        userId: filters.userId,
        createdAtFrom: filters.createdAtFrom,
        createdAtTo: filters.createdAtTo,
        sort: filters.sort,
        page: filters.page,
        pageSize: filters.pageSize,
      }),
    { signal },
  );
}

export async function fetchAdminPost(postId: string, signal?: AbortSignal) {
  return getApiClient().request<AdminPostDetailResponse>("/posts/" + postId, {
    signal,
  });
}

export async function moderateAdminPost(
  postId: string,
  status: "APPROVED" | "REJECTED",
  rejectionReason?: string,
) {
  return getApiClient().request<AdminPostModerationResponse>(
    "/posts/" + postId + "/moderation",
    {
      method: "PUT",
      body: {
        status,
        rejectionReason: status === "REJECTED" ? rejectionReason : undefined,
      },
    },
  );
}

export async function deleteAdminPost(postId: string, reason: string) {
  return getApiClient().request<void>("/posts/" + postId, {
    method: "DELETE",
    body: { reason },
  });
}
