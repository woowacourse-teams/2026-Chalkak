import { delay, http, HttpResponse } from "msw";
import {
  AI_PROMPT_TYPE, checkModel, checkSystemPrompt, checkUserPromptTemplate, DEFAULTS, MAX_OUTPUT_TOKENS_MAX,
  THRESHOLD_MAX, imageDetails, reasoningEfforts, checkMemo,
} from "@/features/ai-prompts/model/ai-prompt";
import { isUuid } from "@/shared/api/uuid";
import type {
  AiPromptFailureType, AiPromptPreviewResponse, AiPromptVersionDetail, AiPromptVersionSummary,
} from "@/shared/api/contracts";

const adminId = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
// Stand-in for the server's model allow-list. mock-* models trigger the failure scenarios below.
const allowedModels = ["gpt-6.1-sol", "gpt-6-luna"];
const failureScenarios: Record<string, AiPromptFailureType> = {
  timeout: "TIMEOUT", "rate-limited": "RATE_LIMITED", quota: "QUOTA_EXCEEDED", upstream: "UPSTREAM_ERROR",
  "bad-request": "BAD_REQUEST", refused: "REFUSED", incomplete: "INCOMPLETE", "invalid-output": "INVALID_OUTPUT",
};
const retryable = new Set<AiPromptFailureType>(["TIMEOUT", "RATE_LIMITED", "UPSTREAM_ERROR"]);

export const aiPromptFixtures: AiPromptVersionDetail[] = [
  {
    promptVersionId: "cccccccc-cccc-4ccc-8ccc-cccccccccc02", type: "PHOTO_REVIEW", version: 2,
    systemPrompt: "당신은 사진 리뷰어입니다.\n구도와 주제 표현을 10점 만점으로 평가하고, 다정한 어조로 한 문단의 피드백을 작성하세요.",
    userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6.1-sol",
    parameters: { imageDetail: "HIGH", maxOutputTokens: 8000, reasoningEffort: "LOW", distinctivePerspectiveThreshold: 7 },
    outputSchemaVersion: "1", memo: "피드백 어조를 부드럽게 조정", createdByAdminId: adminId, createdAt: "2026-10-20T01:00:00Z",
  },
  {
    promptVersionId: "cccccccc-cccc-4ccc-8ccc-cccccccccc01", type: "PHOTO_REVIEW", version: 1,
    systemPrompt: "당신은 사진 리뷰어입니다. 구도와 주제 표현을 평가하세요.",
    userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
    parameters: { imageDetail: "HIGH", maxOutputTokens: 8000, reasoningEffort: null, distinctivePerspectiveThreshold: 7 },
    outputSchemaVersion: "1", memo: null, createdByAdminId: adminId, createdAt: "2026-10-19T00:00:00Z",
  },
];
let versions = structuredClone(aiPromptFixtures);
export function resetAiPromptMocks() { versions = structuredClone(aiPromptFixtures); }

const path = "*/api/v1/admin/ai-prompt-versions";
const error = (message: string, status = 400) => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });

function summary(version: AiPromptVersionDetail): AiPromptVersionSummary {
  const { systemPrompt: _s, userPromptTemplate: _t, parameters: _p, ...rest } = version;
  void _s; void _t; void _p;
  return rest;
}

type Body = Record<string, unknown>;
function validateBody(body: Body) {
  if (typeof body.systemPrompt !== "string" || typeof body.userPromptTemplate !== "string" || typeof body.model !== "string") {
    return "프롬프트 정보를 확인해 주세요.";
  }
  if (body.type !== AI_PROMPT_TYPE) return "지원하지 않는 프롬프트 유형입니다.";
  const message = checkSystemPrompt(body.systemPrompt) ?? checkUserPromptTemplate(body.userPromptTemplate) ?? checkModel(body.model);
  if (message) return message;
  const failure = modelFailure(body.model.trim());
  if (!allowedModels.includes(body.model.trim()) && failure === undefined) return "허용되지 않은 모델입니다.";
  const parameters = (body.parameters ?? {}) as Body;
  if (parameters.imageDetail !== undefined && !imageDetails.includes(parameters.imageDetail as never)) return "이미지 상세도를 확인해 주세요.";
  if (parameters.reasoningEffort !== undefined && !reasoningEfforts.includes(parameters.reasoningEffort as never)) return "추론 강도를 확인해 주세요.";
  const tokens = parameters.maxOutputTokens;
  if (tokens !== undefined && (!Number.isInteger(tokens) || (tokens as number) < 1 || (tokens as number) > MAX_OUTPUT_TOKENS_MAX)) return "최대 출력 토큰을 확인해 주세요.";
  const threshold = parameters.distinctivePerspectiveThreshold;
  if (threshold !== undefined && (typeof threshold !== "number" || threshold < 0 || threshold > THRESHOLD_MAX)) return "독특한 시각 기준 점수를 확인해 주세요.";
  return null;
}
function modelFailure(model: string) {
  return model.startsWith("mock-") ? failureScenarios[model.slice(5)] : undefined;
}

function previewResult(body: Body, failureType: AiPromptFailureType | undefined): AiPromptPreviewResponse {
  if (failureType) {
    const usage = failureType === "INCOMPLETE" ? { inputTokens: 1450, cachedInputTokens: null, outputTokens: 8000, reasoningTokens: 7600 } : null;
    return {
      succeeded: false, failureType, retryable: retryable.has(failureType), result: null, output: null, usage,
      latencyMs: failureType === "TIMEOUT" ? 60_000 : 1800, responseId: usage ? "resp_mock_failed" : null,
    };
  }
  const threshold = ((body.parameters ?? {}) as Body).distinctivePerspectiveThreshold ?? DEFAULTS.distinctivePerspectiveThreshold;
  const output = { compositionScore: 8.2, themeExpressionScore: 7.4 };
  return {
    succeeded: true, failureType: null, retryable: null,
    result: {
      overallScore: 7.8,
      criteria: [{ key: "composition", label: "구도", score: output.compositionScore }, { key: "themeExpression", label: "주제 표현", score: output.themeExpressionScore }],
      distinctivePerspective: output.themeExpressionScore >= (threshold as number),
      feedback: "빛이 번지는 방향을 따라 시선이 자연스럽게 흐르는 사진이에요.\n주제를 한 걸음 더 가까이에서 담아 보면 이야기가 더 또렷해질 거예요.",
    },
    output, usage: { inputTokens: 1450, cachedInputTokens: null, outputTokens: 620, reasoningTokens: 410 },
    latencyMs: 4200, responseId: "resp_mock_succeeded",
  };
}

export function createAiPromptHandlers(isPublicPost: (postId: string) => boolean) {
  return [
    http.post("*/api/v1/admin/ai-prompt-previews", async ({ request }) => {
      const body = await request.json() as Body;
      const invalid = body && typeof body === "object" ? validateBody(body) : "프롬프트 정보를 확인해 주세요.";
      if (invalid) return error(invalid);
      if (typeof body.postId !== "string" || !isUuid(body.postId)) return error("게시물 ID를 확인해 주세요.");
      if (!isPublicPost(body.postId)) return error("공개된 게시물을 찾을 수 없습니다.", 404);
      await delay(300);
      const scenario = new URL(request.url).searchParams.get("scenario");
      return HttpResponse.json(previewResult(body, failureScenarios[scenario ?? ""] ?? modelFailure((body.model as string).trim())));
    }),
    http.get(path, ({ request }) => {
      const params = new URL(request.url).searchParams;
      const page = Number(params.get("page") ?? 1), pageSize = Number(params.get("pageSize") ?? 20);
      if (params.get("type") !== AI_PROMPT_TYPE) return error("조회 조건이 올바르지 않습니다.");
      if (!Number.isInteger(page) || page < 1 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100) return error("조회 조건이 올바르지 않습니다.");
      const sorted = [...versions].sort((a, b) => b.version - a.version);
      return HttpResponse.json({ currentPage: page, pageSize, hasNext: page * pageSize < sorted.length, promptVersions: sorted.slice((page - 1) * pageSize, page * pageSize).map(summary) });
    }),
    http.get(path + "/:id", ({ params }) => {
      const version = versions.find((row) => row.promptVersionId === params.id);
      return version ? HttpResponse.json(version) : error("프롬프트 버전을 찾을 수 없습니다.", 404);
    }),
    http.post(path, async ({ request }) => {
      const body = await request.json() as Body;
      const invalid = body && typeof body === "object" ? validateBody(body) : "프롬프트 정보를 확인해 주세요.";
      if (invalid) return error(invalid);
      if (body.memo !== undefined && body.memo !== null && (typeof body.memo !== "string" || checkMemo(body.memo))) return error("메모는 500자 이하로 입력해 주세요.");
      const parameters = (body.parameters ?? {}) as Body;
      const version: AiPromptVersionDetail = {
        promptVersionId: crypto.randomUUID(), type: "PHOTO_REVIEW", version: Math.max(0, ...versions.map((row) => row.version)) + 1,
        systemPrompt: body.systemPrompt as string, userPromptTemplate: body.userPromptTemplate as string, model: (body.model as string).trim(),
        parameters: {
          imageDetail: (parameters.imageDetail as never) ?? DEFAULTS.imageDetail, maxOutputTokens: (parameters.maxOutputTokens as number) ?? DEFAULTS.maxOutputTokens,
          reasoningEffort: (parameters.reasoningEffort as never) ?? null,
          distinctivePerspectiveThreshold: (parameters.distinctivePerspectiveThreshold as number) ?? DEFAULTS.distinctivePerspectiveThreshold,
        },
        outputSchemaVersion: "1", memo: typeof body.memo === "string" && body.memo.trim() ? body.memo.trim() : null,
        createdByAdminId: adminId, createdAt: new Date().toISOString(),
      };
      versions.unshift(version);
      return HttpResponse.json(version, { status: 201 });
    }),
  ];
}
