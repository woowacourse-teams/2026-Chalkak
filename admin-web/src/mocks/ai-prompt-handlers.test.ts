import { describe, expect, it } from "vitest";
import { postIds } from "./fixtures";
import { aiPromptFixtures } from "./ai-prompt-handlers";

const api = "http://localhost:8080/api/v1/admin";
const body = {
  type: "PHOTO_REVIEW", systemPrompt: "사진을 평가하세요.", userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
};
function post(path: string, payload: unknown, query = "") {
  return fetch(`${api}${path}${query}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
}

describe("AI prompt mock handlers", () => {
  it("lists versions newest first without prompt bodies", async () => {
    const response = await fetch(`${api}/ai-prompt-versions?type=PHOTO_REVIEW&page=1&pageSize=20`);
    const json = await response.json();
    expect(json).toMatchObject({ currentPage: 1, pageSize: 20, hasNext: false });
    expect(json.promptVersions.map((row: { version: number }) => row.version)).toEqual([2, 1]);
    expect(json.promptVersions[0]).not.toHaveProperty("systemPrompt");
    expect((await fetch(`${api}/ai-prompt-versions`)).status).toBe(400);
  });

  it("returns version detail or the 404 contract", async () => {
    const found = await fetch(`${api}/ai-prompt-versions/${aiPromptFixtures[0].promptVersionId}`);
    expect(await found.json()).toMatchObject({ version: 2, parameters: { reasoningEffort: "LOW" } });
    const missing = await fetch(`${api}/ai-prompt-versions/00000000-0000-4000-8000-000000000000`);
    expect(missing.status).toBe(404);
    expect(await missing.json()).toEqual({ errorCode: "BUSINESS_ERROR", message: "프롬프트 버전을 찾을 수 없습니다." });
  });

  it("creates the next version with defaults and a trimmed memo", async () => {
    const response = await post("/ai-prompt-versions", { ...body, memo: "  첫 조정  " });
    expect(response.status).toBe(201);
    expect(await response.json()).toMatchObject({
      version: 3, memo: "첫 조정", parameters: { imageDetail: "HIGH", maxOutputTokens: 8000, reasoningEffort: null, distinctivePerspectiveThreshold: 7 },
    });
    const list = await (await fetch(`${api}/ai-prompt-versions?type=PHOTO_REVIEW`)).json();
    expect(list.promptVersions[0].version).toBe(3);
  });

  it.each([
    [{ ...body, model: "unknown-model" }, "허용되지 않은 모델입니다."],
    [{ ...body, systemPrompt: "{{topicTitle}}" }, "시스템 프롬프트에는 {{...}} 형식의 변수를 사용할 수 없습니다."],
    [{ ...body, userPromptTemplate: "{{other}}" }, "사용할 수 있는 변수는 {{topicTitle}} 하나뿐입니다."],
  ])("rejects invalid input with a 400 message", async (payload, message) => {
    const response = await post("/ai-prompt-versions", payload);
    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ errorCode: "BUSINESS_ERROR", message });
  });

  it("previews an approved post and reflects the threshold in the verdict", async () => {
    const response = await post("/ai-prompt-previews", { ...body, postId: postIds.approved });
    expect(await response.json()).toMatchObject({ succeeded: true, failureType: null, result: { distinctivePerspective: true } });
    const strict = await post("/ai-prompt-previews", { ...body, parameters: { distinctivePerspectiveThreshold: 9 }, postId: postIds.approved });
    expect((await strict.json()).result.distinctivePerspective).toBe(false);
  });

  it("returns 404 for a post that is not public", async () => {
    const response = await post("/ai-prompt-previews", { ...body, postId: postIds.pending });
    expect(response.status).toBe(404);
  });

  it.each([
    ["mock-timeout", "TIMEOUT", true], ["mock-quota", "QUOTA_EXCEEDED", false], ["mock-refused", "REFUSED", false], ["mock-incomplete", "INCOMPLETE", false],
  ])("returns failure scenarios as HTTP 200 for model %s", async (model, failureType, retryable) => {
    const response = await post("/ai-prompt-previews", { ...body, model, postId: postIds.approved });
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ succeeded: false, failureType, retryable, result: null });
  });

  it("selects a failure scenario through the query string", async () => {
    const response = await post("/ai-prompt-previews", { ...body, postId: postIds.approved }, "?scenario=rate-limited");
    expect(await response.json()).toMatchObject({ succeeded: false, failureType: "RATE_LIMITED", retryable: true });
  });
});
