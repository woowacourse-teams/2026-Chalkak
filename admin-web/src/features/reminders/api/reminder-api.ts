import type { AdminReminder, AdminReminderInput, AdminReminderListResponse } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";

const detailPath = (id: string) => `/reminders/${encodeURIComponent(id)}`;
export function fetchReminders(page: number, signal?: AbortSignal) {
  return getApiClient().request<AdminReminderListResponse>(`/reminders?page=${page}&pageSize=20`, { signal });
}
export function createReminder(body: AdminReminderInput) {
  return getApiClient().request<AdminReminder>("/reminders", { method: "POST", body });
}
export function updateReminder(id: string, body: AdminReminderInput) {
  return getApiClient().request<AdminReminder>(detailPath(id), { method: "PUT", body });
}
export function deleteReminder(id: string) {
  return getApiClient().request<void>(detailPath(id), { method: "DELETE" });
}
