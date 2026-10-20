import { getApiClient } from "@/shared/api/client";
import type {
  AiPromptPreviewRequest,
  AiPromptPreviewResponse,
  AiPromptVersionCreateRequest,
  AiPromptVersionDetail,
  AiPromptVersionListResponse,
} from "@/shared/api/contracts";
import { AI_PROMPT_TYPE } from "../model/ai-prompt";

// The relay waits 70s for the backend's OpenAI call; the browser must wait longer.
export const PREVIEW_TIMEOUT_MS = 75_000;

export function fetchAiPromptVersions(page: number, signal?: AbortSignal) {
  return getApiClient().request<AiPromptVersionListResponse>(
    `/ai-prompt-versions?type=${AI_PROMPT_TYPE}&page=${page}&pageSize=20`, { signal });
}
export function fetchAiPromptVersion(id: string, signal?: AbortSignal) {
  return getApiClient().request<AiPromptVersionDetail>(`/ai-prompt-versions/${encodeURIComponent(id)}`, { signal });
}
export function previewAiPrompt(body: AiPromptPreviewRequest) {
  return getApiClient().request<AiPromptPreviewResponse>("/ai-prompt-previews", { method: "POST", body, timeoutMs: PREVIEW_TIMEOUT_MS });
}
export function createAiPromptVersion(body: AiPromptVersionCreateRequest) {
  return getApiClient().request<AiPromptVersionDetail>("/ai-prompt-versions", { method: "POST", body });
}
