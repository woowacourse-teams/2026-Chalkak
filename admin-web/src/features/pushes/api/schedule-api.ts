import type { TopicPushSchedule, TopicPushScheduleListResponse } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";

const path = "/topic-push-schedules";

export function fetchSchedules(signal?: AbortSignal) {
  return getApiClient().request<TopicPushScheduleListResponse>(path, { signal });
}
export function createSchedule(sendTime: string) {
  return getApiClient().request<TopicPushSchedule>(path, { method: "POST", body: { sendTime } });
}
export function updateSchedule(scheduleId: string, sendTime: string) {
  return getApiClient().request<TopicPushSchedule>(`${path}/${encodeURIComponent(scheduleId)}`, { method: "PUT", body: { sendTime } });
}
export function deleteSchedule(scheduleId: string) {
  return getApiClient().request<void>(`${path}/${encodeURIComponent(scheduleId)}`, { method: "DELETE" });
}
