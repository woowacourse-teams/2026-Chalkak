import { getApiClient } from "@/shared/api/client";
import type { Banner, BannerInput, BannerListResponse } from "../model/banner";

// The server relay maps this GET to /api/v1/banners; writes remain under admin.
export function fetchBanners(page: number, signal?: AbortSignal) {
  return getApiClient().request<BannerListResponse>(`/banners?page=${page}&pageSize=20`, { signal });
}
export function createBanner(body: BannerInput) {
  return getApiClient().request<Banner>("/banners", { method: "POST", body });
}
export function deleteBanner(id: string) {
  return getApiClient().request<void>(`/banners/${encodeURIComponent(id)}`, { method: "DELETE" });
}
