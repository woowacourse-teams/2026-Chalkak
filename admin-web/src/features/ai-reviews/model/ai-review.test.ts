import { describe, expect, it } from "vitest";
import { buildAiReviewApiPath, buildAiReviewListHref, formatCount, hasAiReviewFilters, readAiReviewQuery } from "./ai-review";

const uuid = "cccccccc-cccc-4ccc-8ccc-cccccccccc02";
const read = (search: string) => readAiReviewQuery(new URLSearchParams(search));

describe("AI review query", () => {
  it("reads valid filters and page", () => {
    expect(read(`status=FAILED&promptVersionId=${uuid}&page=3`)).toEqual({ status: "FAILED", promptVersionId: uuid, page: 3 });
  });

  it("drops unknown status, non-UUID version, and unsafe pages", () => {
    expect(read("status=DONE&promptVersionId=abc&page=0")).toEqual({ status: undefined, promptVersionId: undefined, page: 1 });
    expect(read("page=1.5").page).toBe(1);
    expect(read("page=99999999999").page).toBe(1);
    expect(read("page=x").page).toBe(1);
  });

  it("builds URLs without defaults and resets nothing implicitly", () => {
    expect(buildAiReviewListHref({ page: 1 })).toBe("/ai-reviews");
    expect(buildAiReviewListHref({ status: "QUEUED", promptVersionId: uuid, page: 2 })).toBe(`/ai-reviews?status=QUEUED&promptVersionId=${uuid}&page=2`);
    expect(buildAiReviewApiPath({ status: "COMPLETED", page: 1 })).toBe("/ai-reviews?status=COMPLETED&page=1&pageSize=20");
  });

  it("detects applied filters and formats null counts", () => {
    expect(hasAiReviewFilters({ page: 4 })).toBe(false);
    expect(hasAiReviewFilters({ status: "FAILED", page: 1 })).toBe(true);
    expect(formatCount(null)).toBe("-");
    expect(formatCount(1450)).toBe("1,450");
  });
});
