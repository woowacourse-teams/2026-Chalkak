"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { AdminAppVersionInput, AdminAppVersionListResponse, AppPlatform } from "@/shared/api/contracts";
import { queryKeys } from "@/shared/query/query-client";
import { fetchAppVersions, saveAppVersion } from "./app-version-api";

export function useAppVersions() {
  return useQuery({ queryKey: queryKeys.appVersions, queryFn: ({ signal }) => fetchAppVersions(signal) });
}

export function useSaveAppVersion(platform: AppPlatform) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (input: AdminAppVersionInput) => saveAppVersion(platform, input),
    retry: false,
    onSuccess: async (saved) => {
      await client.cancelQueries({ queryKey: queryKeys.appVersions });
      client.setQueryData<AdminAppVersionListResponse>(queryKeys.appVersions, (current) => current
        ? { appVersions: current.appVersions.map((row) => row.platform === platform ? saved : row) } : current);
      void client.invalidateQueries({ queryKey: queryKeys.auditLogs });
    },
  });
}
