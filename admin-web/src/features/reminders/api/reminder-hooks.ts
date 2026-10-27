"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AdminReminderInput } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";
import { createReminder, deleteReminder, fetchReminders, updateReminder } from "./reminder-api";

export function useReminders(page: number) {
  return useQuery({ queryKey: [...queryKeys.reminders, "list", page], queryFn: ({ signal }) => fetchReminders(page, signal), refetchInterval: 30_000 });
}
export function useReminderMutations() {
  const client = useQueryClient();
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: queryKeys.reminders }),
      client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
    ]);
  };
  return {
    create: useMutation({ mutationFn: createReminder, onSuccess: refresh }),
    update: useMutation({ mutationFn: ({ id, body }: { id: string; body: AdminReminderInput }) => updateReminder(id, body), onSettled: refresh }),
    remove: useMutation({ mutationFn: (id: string) => deleteReminder(id), onSettled: refresh }),
  };
}
