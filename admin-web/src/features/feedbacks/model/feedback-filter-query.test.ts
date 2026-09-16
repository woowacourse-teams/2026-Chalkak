import { describe, expect, it } from "vitest";

import { readAdminFeedbackFilters } from "./feedback-filter-query";

describe("readAdminFeedbackFilters", () => {
  it("defaults to the newest first page", () => {
    expect(readAdminFeedbackFilters(new URLSearchParams())).toEqual({
      sort: "createdAtDesc",
      page: 1,
      pageSize: 20,
    });
  });

  it("accepts supported URL filters", () => {
    expect(readAdminFeedbackFilters(new URLSearchParams("sort=createdAtAsc&page=3&pageSize=50"))).toEqual({
      sort: "createdAtAsc",
      page: 3,
      pageSize: 50,
    });
  });

  it("falls back safely for invalid values", () => {
    expect(readAdminFeedbackFilters(new URLSearchParams("sort=likesDesc&page=0&pageSize=500"))).toEqual({
      sort: "createdAtDesc",
      page: 1,
      pageSize: 100,
    });
  });
});
