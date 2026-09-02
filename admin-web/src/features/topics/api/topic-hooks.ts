"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/shared/query/query-client";
import { createAdminTopic, deleteAdminTopic, fetchAdminTopic, fetchAdminTopics, updateAdminTopic, type AdminTopicFilters, type AdminTopicMutation } from "./topic-api";

export function useAdminTopics(filters: AdminTopicFilters) {
  return useQuery({ queryKey: queryKeys.topics.list(filters), queryFn: ({ signal }) => fetchAdminTopics(filters, signal) });
}

export function useAdminTopic(topicId: string, enabled = true) {
  return useQuery({ queryKey: queryKeys.topics.detail(topicId), queryFn: ({ signal }) => fetchAdminTopic(topicId, signal), enabled });
}

export function useCreateAdminTopic() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: createAdminTopic,
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.topics.lists }),
        client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}

export function useUpdateAdminTopic() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ topicId, body }: { topicId: string; body: AdminTopicMutation }) => updateAdminTopic(topicId, body),
    onSuccess: async (result) => {
      client.setQueryData(queryKeys.topics.detail(result.topicId), result);
      await Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.topics.lists }),
        client.invalidateQueries({ queryKey: queryKeys.posts.all }),
        client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}

export function useDeleteAdminTopic() {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ topicId, reason }: { topicId: string; reason: string }) => deleteAdminTopic(topicId, reason),
    onSuccess: async () => {
      await Promise.all([
        client.invalidateQueries({ queryKey: queryKeys.topics.all }),
        client.invalidateQueries({ queryKey: queryKeys.posts.all }),
        client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
      ]);
    },
  });
}
