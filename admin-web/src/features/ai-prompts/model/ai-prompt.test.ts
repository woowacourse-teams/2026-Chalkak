import { describe, expect, it } from "vitest";
import type { AiPromptVersionDetail } from "@/shared/api/contracts";
import {
  checkMemo, checkPostId, checkSystemPrompt, checkUserPromptTemplate, draftFingerprint, draftFromVersion,
  emptyDraft, failureTypeLabel, formatLatency, formatTokenCount, toCreateRequest, toPlainDecimal, toVersionInput, validateDraft,
  type AiPromptDraft,
} from "./ai-prompt";

const valid: AiPromptDraft = { ...emptyDraft(), systemPrompt: "사진을 평가하세요.", model: "gpt-6-luna" };

describe("AI prompt draft validation", () => {
  it("starts with the documented defaults and requires the free-text fields", () => {
    const draft = emptyDraft();
    expect(draft).toMatchObject({
      userPromptTemplate: '{"주제": "{{topicTitle}}"}', imageDetail: "HIGH", maxOutputTokens: "8000",
      reasoningEffort: "", distinctivePerspectiveThreshold: "7.0",
    });
    expect(Object.keys(validateDraft(draft))).toEqual(["systemPrompt", "model"]);
    expect(validateDraft(valid)).toEqual({});
  });

  it("rejects blank and over-long text by UTF-16 length", () => {
    expect(checkSystemPrompt("  \n")).toContain("입력");
    expect(checkSystemPrompt("a".repeat(50_000))).toBeNull();
    expect(checkSystemPrompt("a".repeat(50_001))).toContain("50,000자");
    expect(checkUserPromptTemplate("a".repeat(2_001))).toContain("2,000자");
    expect(validateDraft({ ...valid, model: "m".repeat(101) }).model).toContain("100자");
  });

  it("rejects any template variable in the system prompt", () => {
    expect(checkSystemPrompt("주제는 {{topicTitle}} 입니다")).toContain("변수");
    expect(checkSystemPrompt("{{ other }}")).toContain("변수");
    expect(checkSystemPrompt("중괄호 { 하나 } 는 허용")).toBeNull();
    expect(checkSystemPrompt("{{a{b}}")).toBeNull();
    expect(checkSystemPrompt("{{x}}")).toContain("변수");
    expect(checkSystemPrompt("{{a{b}}")).toBeNull();
    expect(checkSystemPrompt("{{x}}")).toContain("변수");
  });

  it("allows only {{topicTitle}} in the user prompt template, including inside JSON", () => {
    expect(checkUserPromptTemplate('{"주제": "{{topicTitle}}"}')).toBeNull();
    expect(checkUserPromptTemplate("{{topicTitle}} / {{topicTitle}}")).toBeNull();
    expect(checkUserPromptTemplate("주제 없음")).toBeNull();
    expect(checkUserPromptTemplate("{{userName}}")).toContain("{{topicTitle}}");
    expect(checkUserPromptTemplate("{{ topicTitle }}")).toContain("{{topicTitle}}");
    expect(checkUserPromptTemplate("{{}}")).toContain("{{topicTitle}}");
    expect(checkUserPromptTemplate("{{a{{topicTitle}}")).toBeNull();
    expect(checkUserPromptTemplate("{{{topicTitle}}}")).toBeNull();
    expect(checkUserPromptTemplate("{{topicTitle}} {{postId}}")).toContain("하나뿐");
  });

  it("counts the memo in Unicode code points after trimming", () => {
    expect(checkMemo(`  ${"😀".repeat(500)}  `)).toBeNull();
    expect(checkMemo("😀".repeat(501))).toContain("500자");
    expect("😀".repeat(500).length).toBe(1000);
  });

  it.each([
    ["0", false], ["1", true], ["16000", true], ["16001", false], ["1.5", false], ["-1", false], ["1e3", false], ["abc", false],
  ])("validates maxOutputTokens %s", (value, ok) => {
    const errors = validateDraft({ ...valid, maxOutputTokens: value });
    expect(Boolean(errors.maxOutputTokens)).toBe(!ok);
  });

  it.each([
    ["0", true], ["7.0", true], ["10", true], ["10.1", false], ["-0.1", false], [".5", false], ["NaN", false], ["1e1", false],
  ])("validates threshold %s", (value, ok) => {
    expect(Boolean(validateDraft({ ...valid, distinctivePerspectiveThreshold: value }).distinctivePerspectiveThreshold)).toBe(!ok);
  });

  it("treats empty number fields as server defaults", () => {
    const draft = { ...valid, maxOutputTokens: " ", distinctivePerspectiveThreshold: "" };
    expect(validateDraft(draft)).toEqual({});
    expect(toVersionInput(draft).parameters).toEqual({ imageDetail: "HIGH" });
  });

  it("validates post ids as UUIDs", () => {
    expect(checkPostId("")).toContain("입력");
    expect(checkPostId("not-a-uuid")).toContain("UUID");
    expect(checkPostId(" 22222222-2222-4222-8222-222222222222 ")).toBeNull();
  });
});

describe("AI prompt request mapping", () => {
  it("sends numbers as numbers and omits an unset reasoning effort", () => {
    expect(toVersionInput({ ...valid, model: " gpt-6-luna ", maxOutputTokens: "4000", distinctivePerspectiveThreshold: "6.5" })).toEqual({
      type: "PHOTO_REVIEW", systemPrompt: "사진을 평가하세요.", userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
      parameters: { imageDetail: "HIGH", maxOutputTokens: 4000, distinctivePerspectiveThreshold: 6.5 },
    });
    expect(toVersionInput({ ...valid, reasoningEffort: "LOW", imageDetail: "AUTO" }).parameters)
      .toMatchObject({ imageDetail: "AUTO", reasoningEffort: "LOW" });
  });

  it("adds a trimmed memo only when it is not empty", () => {
    expect(toCreateRequest({ ...valid, memo: "  톤 조정  " }).memo).toBe("톤 조정");
    expect(toCreateRequest({ ...valid, memo: "   " })).not.toHaveProperty("memo");
  });

  it("fingerprints everything except the memo", () => {
    expect(draftFingerprint({ ...valid, memo: "다른 메모" })).toBe(draftFingerprint(valid));
    for (const change of [{ systemPrompt: "다른 프롬프트" }, { userPromptTemplate: "다른" }, { model: "gpt-6.1-sol" },
      { imageDetail: "LOW" }, { maxOutputTokens: "100" }, { reasoningEffort: "HIGH" }, { distinctivePerspectiveThreshold: "5" }] as const) {
      expect(draftFingerprint({ ...valid, ...change })).not.toBe(draftFingerprint(valid));
    }
  });

  it("prefills from a version without copying the memo", () => {
    const version: AiPromptVersionDetail = {
      promptVersionId: "p", type: "PHOTO_REVIEW", version: 3, systemPrompt: "S", userPromptTemplate: "T", model: "gpt-6-luna",
      parameters: { imageDetail: "LOW", maxOutputTokens: 6000, reasoningEffort: null, distinctivePerspectiveThreshold: 6.5 },
      outputSchemaVersion: "1", memo: "이전 메모", createdByAdminId: "a", createdAt: "2026-10-20T00:00:00Z",
    };
    expect(draftFromVersion(version)).toEqual({
      systemPrompt: "S", userPromptTemplate: "T", model: "gpt-6-luna", imageDetail: "LOW", maxOutputTokens: "6000",
      reasoningEffort: "", distinctivePerspectiveThreshold: "6.5", memo: "",
    });
  });
});

describe("threshold round trip through a stored version", () => {
  const versionWith = (threshold: number): AiPromptVersionDetail => ({
    promptVersionId: "p", type: "PHOTO_REVIEW", version: 1, systemPrompt: "S", userPromptTemplate: "T", model: "gpt-6-luna",
    parameters: { imageDetail: "HIGH", maxOutputTokens: 8000, reasoningEffort: null, distinctivePerspectiveThreshold: threshold },
    outputSchemaVersion: "1", memo: null, createdByAdminId: "a", createdAt: "2026-10-20T00:00:00Z",
  });

  it("keeps a tiny threshold valid and numerically identical without exponent notation", () => {
    const typed = { ...valid, distinctivePerspectiveThreshold: "0.0000001" };
    expect(validateDraft(typed)).toEqual({});
    const sent = toVersionInput(typed).parameters?.distinctivePerspectiveThreshold;
    expect(sent).toBe(1e-7);

    const loaded = draftFromVersion(versionWith(sent as number));
    expect(loaded.distinctivePerspectiveThreshold).toBe("0.0000001");
    expect(validateDraft({ ...loaded, systemPrompt: "S", model: "gpt-6-luna", userPromptTemplate: "T" })).toEqual({});
    expect(toVersionInput({ ...loaded, systemPrompt: "S", userPromptTemplate: "T" }).parameters?.distinctivePerspectiveThreshold).toBe(1e-7);
  });

  it("formats numbers in plain decimal notation", () => {
    expect(toPlainDecimal(7)).toBe("7");
    expect(toPlainDecimal(7.5)).toBe("7.5");
    expect(toPlainDecimal(0)).toBe("0");
    expect(toPlainDecimal(10)).toBe("10");
    expect(toPlainDecimal(1e-7)).toBe("0.0000001");
    expect(toPlainDecimal(1.5e-10)).toBe("0.00000000015");
    expect(toPlainDecimal(1.5e21)).toBe("1500000000000000000000");
  });
});

describe("AI prompt display helpers", () => {
  it("labels failure types and unreported values", () => {
    expect(failureTypeLabel("QUOTA_EXCEEDED")).toBe("사용 한도 소진");
    expect(failureTypeLabel(null)).toBe("알 수 없는 오류");
    expect(formatTokenCount(null)).toBe("미보고");
    expect(formatTokenCount(1234)).toBe("1,234토큰");
    expect(formatLatency(null)).toBe("-");
    expect(formatLatency(2345)).toBe("2.3초");
  });
});
