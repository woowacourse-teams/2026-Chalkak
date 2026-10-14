import type { AdminNotice, AdminNoticeInput, AdminNoticeListResponse } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";

const detailPath = (id: string) => `/notices/${encodeURIComponent(id)}`;
export function fetchNotices(page: number, signal?: AbortSignal) {
  return getApiClient().request<AdminNoticeListResponse>(`/notices?page=${page}&pageSize=20`, { signal });
}
export function fetchNotice(id: string, signal?: AbortSignal) {
  return getApiClient().request<AdminNotice>(detailPath(id), { signal });
}
export function createNotice(body: AdminNoticeInput) {
  return getApiClient().request<AdminNotice>("/notices", { method: "POST", body });
}
export function updateNotice(id: string, body: AdminNoticeInput) {
  return getApiClient().request<AdminNotice>(detailPath(id), { method: "PUT", body });
}
export function deleteNotice(id: string, reason: string) {
  return getApiClient().request<void>(detailPath(id), { method: "DELETE", body: { reason } });
}
