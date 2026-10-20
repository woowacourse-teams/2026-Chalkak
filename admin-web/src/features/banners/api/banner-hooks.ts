"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/shared/query/query-client";
import { createBanner, deleteBanner, fetchBanners } from "./banner-api";

export function useBanners(page: number) {
  return useQuery({ queryKey: [...queryKeys.banners, page], queryFn: ({ signal }) => fetchBanners(page, signal) });
}
export function useBannerMutations() {
  const client = useQueryClient();
  const refresh = async () => { await Promise.all([
    client.invalidateQueries({ queryKey: queryKeys.banners }),
    client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
  ]); };
  return {
    create: useMutation({ mutationFn: createBanner, onSuccess: refresh }),
    remove: useMutation({ mutationFn: deleteBanner, onSettled: refresh }),
  };
}
