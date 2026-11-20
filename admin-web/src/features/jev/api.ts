import { getApiClient } from "@/shared/api/client";

export type Decision = "APPROVE" | "HUMAN_REVIEW" | "REJECT";
export type Disposition = "AUTO_APPROVED" | "ADMIN_REVIEW" | "SKIPPED";
export type PostStatus = "APPROVED" | "PENDING" | "REJECTED" | "VALIDATING";
export type Role = "JEV_TOPIC_CLASSIFICATION" | "JEV_PHOTO_MODERATION";
export type Sampling = "RANDOM" | "ERROR_ANALYSIS" | "UNSPECIFIED";

export interface RunRow {
  jobId: string;
  postId: string;
  topicId: string;
  topic: string;
  preview: boolean;
  disposition: Disposition | null;
  recommendation: Decision | null;
  postStatus: PostStatus | null;
  goldDecision: Decision | null;
  slackStatus: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface Answer {
  choice: string;
  confidence: number | null;
  probabilities: Record<string, number>;
}

export interface Evaluation {
  answers: Record<string, Answer>;
  decisions: Record<string, Decision>;
  recommendation: Decision | null;
  error: string | null;
}

export interface Call {
  callId: string;
  startedAt: string | null;
  receivedAt: string | null;
  httpStatus: number | null;
  latencyMs: number | null;
  inputTokens: number | null;
  outputTokens: number | null;
  errorCode: string | null;
  rawResponse: string | null;
}

export interface Run {
  jobId: string;
  postId: string;
  topicId: string;
  topic: string;
  preview: boolean;
  classificationId: string | null;
  category: string | null;
  classificationCalls: { calls: Call[] } | null;
  classificationEvaluation: Evaluation | null;
  photoCalls: { calls: Call[] } | null;
  evaluation: Evaluation | null;
  dispatchAttempts: number;
  dispatchError: string | null;
  createdAt: string;
  finishedAt: string | null;
}

export interface GoldInput {
  decision: Decision;
  questions: Record<string, Decision>;
  reason: string;
  sampling: Sampling;
}

export interface RunDetail {
  result: Run;
  model: string;
  classificationPromptId: string | null;
  photoPromptId: string | null;
  disposition: Disposition | null;
  applicationReason: string | null;
  appliedAt: string | null;
  postStatus: PostStatus | null;
  postDeletedAt: string | null;
  originalImageUrl: string | null;
  slackDelivery: { status: string; attempts: number; availableAt: string | null;
    history: Array<{ token: string; startedAt: string; error: string | null; httpStatus: number | null }> } | null;
  goldLabel: (GoldInput & { evaluatedAt: string; evaluatedBy: string }) | null;
  rejectionSuggestions: Record<string, string>;
}

export interface Agreement {
  rated: number;
  matched: number;
  falseApprovals: number;
  falseRejections: number;
  missingPredictions: number;
  matchPercent: number | null;
}

export interface Metrics {
  runs: number;
  completed: number;
  autoApproved: number;
  adminReview: number;
  skipped: number;
  technicalErrors: number;
  truncated: boolean;
  finalAgreement: Agreement;
  bySampling: Record<Sampling, Agreement>;
  questions: Record<string, Agreement>;
  ratedAutoApprovals: number;
  falseAutoApprovals: number;
  repeatedGroups: number;
  stableGroups: number;
  consistencyPercent: number | null;
  usage: { reservedAttempts: number; observedHttpCalls: number; timedHttpCalls: number;
    httpP99Ms: number | null; knownInputTokens: number; knownOutputTokens: number;
    unknownInputUsageCalls: number; unknownOutputUsageCalls: number };
}

export interface PromptConfig {
  questions: Record<string, { type: "choice"; instructions: string; criteria: Record<string, string> }>;
  categoryRules: Record<string, string>;
  categoryInstructions: Record<string, string>;
}

export interface PromptSummary {
  id: string;
  role: Role;
  version: number;
  model: string;
  active: boolean;
  memo: string | null;
  createdAt: string;
}
export type Prompt = PromptSummary & { configuration: PromptConfig };

const runs = "/jev-moderation-runs";
const prompts = "/jev-prompt-versions";
const previews = "/jev-previews";
export const jevModel = "jev-1.14.0";

export const jevApi = {
  history: (query: URLSearchParams, signal?: AbortSignal) =>
    getApiClient().request<{rows: RunRow[]; hasNext: boolean}>(runs + "?" + query, { signal }),
  detail: (id: string, signal?: AbortSignal) =>
    getApiClient().request<RunDetail>(runs + "/" + encodeURIComponent(id), { signal }),
  evaluate: (id: string, body: GoldInput) =>
    getApiClient().request<GoldInput>(runs + "/" + encodeURIComponent(id) + "/evaluation", { method: "PUT", body }),
  correct: (postId: string, body: { status: "APPROVED" | "REJECTED"; reason: string }) =>
    getApiClient().request<{ moderatedBy: string | null }>("/posts/" + encodeURIComponent(postId) + "/moderation-correction", { method: "PUT", body }),
  metrics: (query: URLSearchParams, signal?: AbortSignal) =>
    getApiClient().request<Metrics>(runs + "/metrics?" + query, { signal }),
  prompts: (role: Role, page: number, signal?: AbortSignal) =>
    getApiClient().request<{promptVersions: PromptSummary[]; hasNext: boolean}>(prompts + "?" + new URLSearchParams({ role, page: String(page) }), { signal }),
  prompt: (id: string, signal?: AbortSignal) =>
    getApiClient().request<Prompt>(prompts + "/" + encodeURIComponent(id), { signal }),
  createPrompt: (body: {role: Role; model: string; configuration: PromptConfig; memo: string | null}) =>
    getApiClient().request<Prompt>(prompts, { method: "POST", body }),
  activate: (id: string, active: boolean) =>
    getApiClient().request<Prompt>(prompts + "/" + encodeURIComponent(id) + "/activation", { method: "PUT", body: { active } }),
  createPreview: (body: {postId: string; topicId: string; draft: {model: string; classification: PromptConfig; photo: PromptConfig}}) =>
    getApiClient().request<Run>(previews, { method: "POST", body }),
  preview: (id: string, signal?: AbortSignal) =>
    getApiClient().request<Run>(previews + "/" + encodeURIComponent(id), { signal }),
};
