"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AdminNoticeInput } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";
import { createNotice, deleteNotice, fetchNotice, fetchNotices, updateNotice } from "./notice-api";

export function useNotices(page: number) {
  return useQuery({ queryKey: [...queryKeys.notices, "list", page], queryFn: ({ signal }) => fetchNotices(page, signal), refetchInterval: 30_000 });
}
export function useNotice(id: string) {
  return useQuery({ queryKey: [...queryKeys.notices, "detail", id], queryFn: ({ signal }) => fetchNotice(id, signal), refetchInterval: 30_000 });
}
export function useNoticeMutations() {
  const client = useQueryClient();
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: queryKeys.notices }),
      client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
    ]);
  };
  return {
    create: useMutation({ mutationFn: createNotice, onSuccess: refresh }),
    update: useMutation({ mutationFn: ({ id, body }: { id: string; body: AdminNoticeInput }) => updateNotice(id, body), onSettled: refresh }),
    remove: useMutation({ mutationFn: ({ id, reason }: { id: string; reason: string }) => deleteNotice(id, reason), onSettled: refresh }),
  };
}
