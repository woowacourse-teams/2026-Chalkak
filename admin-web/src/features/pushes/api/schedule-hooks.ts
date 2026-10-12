"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { queryKeys } from "@/shared/query/query-client";
import { createSchedule, deleteSchedule, fetchSchedules, updateSchedule } from "./schedule-api";

export function useSchedules() {
  return useQuery({ queryKey: queryKeys.pushes, queryFn: ({ signal }) => fetchSchedules(signal) });
}

export function useScheduleMutations() {
  const client = useQueryClient();
  const refresh = async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: queryKeys.pushes }),
      client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
    ]);
  };
  const onError = (error: Error) => {
    if (error instanceof ApiError && error.status === 404) void refresh();
  };
  const create = useMutation({ mutationFn: createSchedule, onSuccess: refresh, onError });
  const update = useMutation({ mutationFn: ({ scheduleId, sendTime }: { scheduleId: string; sendTime: string }) => updateSchedule(scheduleId, sendTime), onSuccess: refresh, onError });
  const remove = useMutation({ mutationFn: deleteSchedule, onSuccess: refresh, onError });
  return { create, update, remove };
}
