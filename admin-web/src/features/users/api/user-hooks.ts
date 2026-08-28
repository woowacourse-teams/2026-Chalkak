"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import type { AdminUserDetailResponse } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";

import {
  fetchAdminUser,
  fetchAdminUsers,
  updateAdminUserStatus,
  type AdminUserFilters,
} from "./user-api";

export function useAdminUsers(filters: AdminUserFilters) {
  return useQuery({
    queryKey: queryKeys.users.list(filters),
    queryFn: ({ signal }) => fetchAdminUsers(filters, signal),
  });
}

export function useAdminUser(userId: string) {
  return useQuery({
    queryKey: queryKeys.users.detail(userId),
    queryFn: ({ signal }) => fetchAdminUser(userId, signal),
  });
}

export function useUpdateAdminUserStatus() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      userId,
      status,
      reason,
    }: {
      userId: string;
      status: "ACTIVE" | "BANNED";
      reason: string;
    }) => updateAdminUserStatus(userId, status, reason),
    onSuccess: async (result) => {
      queryClient.setQueryData<AdminUserDetailResponse>(
        queryKeys.users.detail(result.userId),
        (current) => (current ? { ...current, status: result.status } : current),
      );
      await queryClient.invalidateQueries({ queryKey: queryKeys.users.lists });
    },
  });
}
