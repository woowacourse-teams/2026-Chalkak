import { describe, expect, it } from "vitest";
import { aiReviewFixtures } from "./ai-review-handlers";

const api = "http://localhost:8080/api/v1/admin";
const list = async (query = "") => (await fetch(`${api}/ai-reviews${query}`)).json();

describe("AI review mock handlers", () => {
  it("lists newest request first and pages by hasNext", async () => {
    const first = await list();
    expect(first).toMatchObject({ currentPage: 1, pageSize: 20, hasNext: true });
    expect(first.aiReviews).toHaveLength(20);
    const times = first.aiReviews.map((row: { lastRequestedAt: string }) => row.lastRequestedAt);
    expect([...times].sort().reverse()).toEqual(times);
    expect(first.aiReviews[0]).not.toHaveProperty("output");
    expect(first.aiReviews[0].post).not.toHaveProperty("imageUrl");
    const second = await list("?page=2");
    expect(second.aiReviews).toHaveLength(aiReviewFixtures.length - 20);
    expect(second.hasNext).toBe(false);
  });

  it("covers every status, a retry-pending review with an error code, and hidden reviews", () => {
    expect(new Set(aiReviewFixtures.map((row) => row.status))).toEqual(new Set(["QUEUED", "PROCESSING", "COMPLETED", "FAILED"]));
    expect(aiReviewFixtures.some((row) => row.status === "QUEUED" && row.errorCode !== null)).toBe(true);
    expect(aiReviewFixtures.filter((row) => !row.visibleToAuthor)).toHaveLength(3);
    expect(aiReviewFixtures.some((row) => row.author.status === "BANNED" && row.visibleToAuthor)).toBe(true);
    expect(aiReviewFixtures.filter((row) => row.status !== "COMPLETED").every((row) => row.result === null && row.output === null && row.overallScore === null)).toBe(true);
  });

  it("filters by status and prompt version", async () => {
    const failed = await list("?status=FAILED");
    expect(failed.aiReviews.map((row: { status: string }) => row.status)).toEqual(["FAILED"]);
    const v1 = await list("?promptVersionId=cccccccc-cccc-4ccc-8ccc-cccccccccc01&pageSize=100");
    expect(v1.aiReviews.length).toBeGreaterThan(0);
    expect(v1.aiReviews.every((row: { promptVersion: { version: number } }) => row.promptVersion.version === 1)).toBe(true);
  });

  it("returns an empty list for an unknown but valid prompt version", async () => {
    expect(await list("?promptVersionId=00000000-0000-4000-8000-000000000000")).toMatchObject({ hasNext: false, aiReviews: [] });
  });

  it.each(["?status=DONE", "?promptVersionId=abc", "?page=0", "?page=x", "?pageSize=0", "?pageSize=101"])("rejects %s with 400", async (query) => {
    const response = await fetch(`${api}/ai-reviews${query}`);
    expect(response.status).toBe(400);
    expect(await response.json()).toEqual({ errorCode: "BUSINESS_ERROR", message: "조회 조건이 올바르지 않습니다." });
  });

  it("returns detail with result and raw output, or 404 / 400", async () => {
    const completed = aiReviewFixtures[0];
    const found = await (await fetch(`${api}/ai-reviews/${completed.aiReviewId}`)).json();
    expect(found).toMatchObject({ status: "COMPLETED", post: { imageUrl: expect.any(String) }, result: { overallScore: completed.overallScore }, output: { compositionScore: completed.overallScore } });
    const queued = await (await fetch(`${api}/ai-reviews/${aiReviewFixtures[2].aiReviewId}`)).json();
    expect(queued).toMatchObject({ result: null, output: null, overallScore: null });
    const missing = await fetch(`${api}/ai-reviews/00000000-0000-4000-8000-000000000000`);
    expect(missing.status).toBe(404);
    expect((await fetch(`${api}/ai-reviews/not-a-uuid`)).status).toBe(400);
  });
});
