"use client";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/shared/query/query-client";
import { createAiPromptVersion, fetchAiPromptVersion, fetchAiPromptVersions, previewAiPrompt } from "./ai-prompt-api";

export function useAiPromptVersions(page: number) {
  return useQuery({ queryKey: [...queryKeys.aiPrompts, "list", page], queryFn: ({ signal }) => fetchAiPromptVersions(page, signal) });
}
// Versions are immutable, so a cached detail never goes stale.
export function useAiPromptVersion(id: string | null) {
  return useQuery({
    queryKey: [...queryKeys.aiPrompts, "detail", id], enabled: id !== null, staleTime: Infinity,
    queryFn: ({ signal }) => fetchAiPromptVersion(id!, signal),
  });
}
export function useAiPromptPreview() {
  return useMutation({ mutationFn: previewAiPrompt });
}
export function useAiPromptVersionMutations() {
  const client = useQueryClient();
  const refresh = async () => { await Promise.all([
    client.invalidateQueries({ queryKey: queryKeys.aiPrompts }),
    client.invalidateQueries({ queryKey: queryKeys.auditLogs }),
  ]); };
  return { create: useMutation({ mutationFn: createAiPromptVersion, onSuccess: refresh }) };
}
