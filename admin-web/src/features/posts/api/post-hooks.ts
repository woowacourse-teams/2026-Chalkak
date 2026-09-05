"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";

import type { AdminPostDetailResponse } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";

import {
  deleteAdminPost,
  fetchAdminPost,
  fetchAdminPosts,
  moderateAdminPost,
  type AdminPostFilters,
} from "./post-api";

export function useAdminPosts(filters: AdminPostFilters) {
  return useQuery({
    queryKey: queryKeys.posts.list(filters),
    queryFn: ({ signal }) => fetchAdminPosts(filters, signal),
  });
}

export function useAdminPost(postId: string) {
  return useQuery({
    queryKey: queryKeys.posts.detail(postId),
    queryFn: ({ signal }) => fetchAdminPost(postId, signal),
  });
}

export function useModerateAdminPost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({
      postId,
      status,
      reason,
    }: {
      postId: string;
      status: "APPROVED" | "REJECTED";
      reason?: string;
    }) => moderateAdminPost(postId, status, reason),
    onSuccess: async (moderation) => {
      queryClient.setQueryData<AdminPostDetailResponse>(
        queryKeys.posts.detail(moderation.postId),
        (current) =>
          current
            ? {
                ...current,
                moderationStatus: moderation.moderationStatus,
                moderatedAt: moderation.moderatedAt,
                moderatedBy: moderation.moderatedBy,
                rejectionReason: moderation.rejectionReason,
              }
            : current,
      );
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.posts.lists }),
        queryClient.invalidateQueries({ queryKey: queryKeys.users.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.topics.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}

export function useDeleteAdminPost() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ postId, reason }: { postId: string; reason: string }) =>
      deleteAdminPost(postId, reason),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: queryKeys.posts.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.users.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.topics.all }),
        queryClient.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}
