import type {
  AiImageDetail,
  AiPromptFailureType,
  AiPromptParametersInput,
  AiPromptVersionCreateRequest,
  AiPromptVersionDetail,
  AiPromptVersionInput,
  AiReasoningEffort,
} from "@/shared/api/contracts";
import { isUuid } from "@/shared/api/uuid";

export const AI_PROMPT_TYPE = "PHOTO_REVIEW";
export const AI_PROMPT_TYPE_LABEL = "사진 AI 리뷰";
// Remove once the user AI review feature (backend #689, PR #716) is deployed; the latest version then applies immediately.
export const LATEST_VERSION_APPLY_CAVEAT = "사용자 AI 리뷰 기능 배포 이후";
export const SYSTEM_PROMPT_MAX = 50_000;
export const USER_PROMPT_TEMPLATE_MAX = 2_000;
export const MODEL_MAX = 100;
export const MEMO_MAX = 500;
export const MAX_OUTPUT_TOKENS_MAX = 16_000;
export const THRESHOLD_MAX = 10;
export const TOPIC_TITLE_VARIABLE = "{{topicTitle}}";
export const DEFAULT_USER_PROMPT_TEMPLATE = `{"주제": "${TOPIC_TITLE_VARIABLE}"}`;
export const DEFAULTS = {
  imageDetail: "HIGH" as AiImageDetail,
  maxOutputTokens: 8_000,
  distinctivePerspectiveThreshold: 7,
};

export const imageDetails: readonly AiImageDetail[] = ["LOW", "HIGH", "AUTO"];
export const reasoningEfforts: readonly AiReasoningEffort[] = ["LOW", "MEDIUM", "HIGH"];

// Form state: numbers stay strings until validated so partial input is never coerced.
export interface AiPromptDraft {
  systemPrompt: string;
  userPromptTemplate: string;
  model: string;
  imageDetail: AiImageDetail;
  maxOutputTokens: string;
  reasoningEffort: AiReasoningEffort | "";
  distinctivePerspectiveThreshold: string;
  memo: string;
}
export type AiPromptDraftErrors = Partial<Record<keyof AiPromptDraft, string>>;

export function emptyDraft(): AiPromptDraft {
  return {
    systemPrompt: "",
    userPromptTemplate: DEFAULT_USER_PROMPT_TEMPLATE,
    model: "",
    imageDetail: DEFAULTS.imageDetail,
    maxOutputTokens: String(DEFAULTS.maxOutputTokens),
    reasoningEffort: "",
    distinctivePerspectiveThreshold: DEFAULTS.distinctivePerspectiveThreshold.toFixed(1),
    memo: "",
  };
}

// String(1e-7) is "1e-7", which parseThreshold rejects; expand the exponent by shifting the decimal point
// instead of toFixed(), which would add binary noise. Assumes a finite, non-negative number.
export function toPlainDecimal(value: number) {
  const text = String(value);
  const match = /^(\d+)(?:\.(\d+))?e([+-]\d+)$/.exec(text);
  if (!match) return text;
  const [, integer, fraction = "", exponent] = match;
  const digits = integer + fraction;
  const point = integer.length + Number(exponent);
  if (point <= 0) return `0.${"0".repeat(-point)}${digits}`;
  if (point >= digits.length) return digits + "0".repeat(point - digits.length);
  return `${digits.slice(0, point)}.${digits.slice(point)}`;
}

// The memo is not copied: each version describes its own change.
export function draftFromVersion(version: AiPromptVersionDetail): AiPromptDraft {
  return {
    systemPrompt: version.systemPrompt,
    userPromptTemplate: version.userPromptTemplate,
    model: version.model,
    imageDetail: version.parameters.imageDetail,
    maxOutputTokens: String(version.parameters.maxOutputTokens),
    reasoningEffort: version.parameters.reasoningEffort ?? "",
    distinctivePerspectiveThreshold: toPlainDecimal(version.parameters.distinctivePerspectiveThreshold),
    memo: "",
  };
}

// Lengths follow Java String.length (UTF-16); only the memo counts Unicode code points.
export function codePointLength(value: string) {
  return Array.from(value).length;
}

// Mirrors the backend placeholder pattern \{\{([^{}]*)}}.
const TEMPLATE_VARIABLE = /\{\{([^{}]*)\}\}/g;

export function checkSystemPrompt(value: string) {
  if (!value.trim()) return "시스템 프롬프트를 입력해 주세요.";
  if (value.length > SYSTEM_PROMPT_MAX) return `시스템 프롬프트는 ${SYSTEM_PROMPT_MAX.toLocaleString()}자 이하로 입력해 주세요.`;
  if (new RegExp(TEMPLATE_VARIABLE).test(value)) return "시스템 프롬프트에는 {{...}} 형식의 변수를 사용할 수 없습니다.";
  return null;
}

export function checkUserPromptTemplate(value: string) {
  if (!value.trim()) return "사용자 프롬프트 템플릿을 입력해 주세요.";
  if (value.length > USER_PROMPT_TEMPLATE_MAX) return `사용자 프롬프트 템플릿은 ${USER_PROMPT_TEMPLATE_MAX.toLocaleString()}자 이하로 입력해 주세요.`;
  if (Array.from(value.matchAll(TEMPLATE_VARIABLE)).some((match) => match[1] !== "topicTitle")) {
    return `사용할 수 있는 변수는 ${TOPIC_TITLE_VARIABLE} 하나뿐입니다.`;
  }
  return null;
}

export function checkModel(value: string) {
  const model = value.trim();
  if (!model) return "모델을 입력해 주세요.";
  if (model.length > MODEL_MAX) return `모델은 ${MODEL_MAX}자 이하로 입력해 주세요.`;
  return null;
}

export function checkMemo(value: string) {
  if (codePointLength(value.trim()) > MEMO_MAX) return `메모는 ${MEMO_MAX}자 이하로 입력해 주세요.`;
  return null;
}

export function parseMaxOutputTokens(value: string) {
  const text = value.trim();
  if (!/^\d+$/.test(text)) return null;
  const parsed = Number(text);
  return parsed >= 1 && parsed <= MAX_OUTPUT_TOKENS_MAX ? parsed : null;
}

export function parseThreshold(value: string) {
  const text = value.trim();
  if (!/^\d+(\.\d+)?$/.test(text)) return null;
  const parsed = Number(text);
  return parsed >= 0 && parsed <= THRESHOLD_MAX ? parsed : null;
}

// An empty number field means "use the server default".
export function validateDraft(draft: AiPromptDraft): AiPromptDraftErrors {
  const errors: AiPromptDraftErrors = {};
  const systemPrompt = checkSystemPrompt(draft.systemPrompt);
  if (systemPrompt) errors.systemPrompt = systemPrompt;
  const userPromptTemplate = checkUserPromptTemplate(draft.userPromptTemplate);
  if (userPromptTemplate) errors.userPromptTemplate = userPromptTemplate;
  const model = checkModel(draft.model);
  if (model) errors.model = model;
  if (draft.maxOutputTokens.trim() && parseMaxOutputTokens(draft.maxOutputTokens) === null) {
    errors.maxOutputTokens = `최대 출력 토큰은 1~${MAX_OUTPUT_TOKENS_MAX.toLocaleString()} 사이의 정수로 입력해 주세요.`;
  }
  if (draft.distinctivePerspectiveThreshold.trim() && parseThreshold(draft.distinctivePerspectiveThreshold) === null) {
    errors.distinctivePerspectiveThreshold = `독특한 시각 기준 점수는 0~${THRESHOLD_MAX} 사이의 숫자로 입력해 주세요.`;
  }
  const memo = checkMemo(draft.memo);
  if (memo) errors.memo = memo;
  return errors;
}

// The version-defining part of a draft; the preview and the created version share it.
export function toVersionInput(draft: AiPromptDraft): AiPromptVersionInput {
  const parameters: AiPromptParametersInput = { imageDetail: draft.imageDetail };
  const maxOutputTokens = parseMaxOutputTokens(draft.maxOutputTokens);
  if (maxOutputTokens !== null) parameters.maxOutputTokens = maxOutputTokens;
  if (draft.reasoningEffort) parameters.reasoningEffort = draft.reasoningEffort;
  const threshold = parseThreshold(draft.distinctivePerspectiveThreshold);
  if (threshold !== null) parameters.distinctivePerspectiveThreshold = threshold;
  return {
    type: AI_PROMPT_TYPE,
    systemPrompt: draft.systemPrompt,
    userPromptTemplate: draft.userPromptTemplate,
    model: draft.model.trim(),
    parameters,
  };
}

export function toCreateRequest(draft: AiPromptDraft): AiPromptVersionCreateRequest {
  const memo = draft.memo.trim();
  return memo ? { ...toVersionInput(draft), memo } : toVersionInput(draft);
}

// Identifies the exact content that was previewed (memo and post are excluded).
export function draftFingerprint(draft: AiPromptDraft) {
  return JSON.stringify(toVersionInput(draft));
}

export function checkPostId(value: string) {
  const postId = value.trim();
  if (!postId) return "미리보기할 게시물 ID를 입력해 주세요.";
  if (!isUuid(postId)) return "게시물 ID는 UUID 형식으로 입력해 주세요.";
  return null;
}

export const failureTypeLabels: Record<AiPromptFailureType, string> = {
  TIMEOUT: "시간 초과",
  RATE_LIMITED: "요청 한도 초과",
  QUOTA_EXCEEDED: "사용 한도 소진",
  UPSTREAM_ERROR: "서버 오류",
  BAD_REQUEST: "잘못된 요청",
  REFUSED: "생성 거절",
  INCOMPLETE: "출력 잘림",
  INVALID_OUTPUT: "출력 형식 오류",
};

export function failureTypeLabel(type: AiPromptFailureType | null) {
  return type ? failureTypeLabels[type] ?? type : "알 수 없는 오류";
}

export function formatVersion(version: number) {
  return `v${version}`;
}

// A null count means the model did not report it.
export function formatTokenCount(value: number | null) {
  return value === null ? "미보고" : `${value.toLocaleString()}토큰`;
}

export function formatLatency(value: number | null) {
  return value === null ? "-" : `${(value / 1000).toFixed(1)}초`;
}
