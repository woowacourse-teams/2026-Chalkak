import type { AdminTopicDetailResponse, AdminTopicListResponse, TopicStatus } from "@/shared/api/contracts";
import { getApiClient } from "@/shared/api/client";
import { serializeQuery } from "@/shared/api/query-string";

export type AdminTopicSort = "topicDateDesc" | "topicDateAsc" | "createdAtDesc" | "createdAtAsc";
export interface AdminTopicFilters { phase?: TopicStatus; dateFrom?: string; dateTo?: string; sort: AdminTopicSort; page: number; pageSize: number; }
export interface AdminTopicMutation { title: string; topicDate: string; startsAt: string; endsAt: string; }

export function fetchAdminTopics(filters: AdminTopicFilters, signal?: AbortSignal) {
  return getApiClient().request<AdminTopicListResponse>("/topics" + serializeQuery({
    phase: filters.phase,
    dateFrom: filters.dateFrom,
    dateTo: filters.dateTo,
    sort: filters.sort,
    page: filters.page,
    pageSize: filters.pageSize,
  }), { signal });
}
export function fetchAdminTopic(topicId: string, signal?: AbortSignal) {
  return getApiClient().request<AdminTopicDetailResponse>("/topics/" + topicId, { signal });
}
export function createAdminTopic(body: AdminTopicMutation) { return getApiClient().request<AdminTopicDetailResponse>("/topics", { method:"POST", body }); }
export function updateAdminTopic(topicId: string, body: AdminTopicMutation) { return getApiClient().request<AdminTopicDetailResponse>("/topics/" + topicId, { method:"PUT", body }); }
export function deleteAdminTopic(topicId: string, reason: string) { return getApiClient().request<void>("/topics/" + topicId, { method:"DELETE", body:{reason} }); }
