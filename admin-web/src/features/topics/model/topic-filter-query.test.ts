import { describe, expect, it } from "vitest";

import { readAdminTopicFilters } from "./topic-filter-query";

describe("readAdminTopicFilters", () => {
  it("reads the supported URL contract", () => {
    expect(readAdminTopicFilters(new URLSearchParams("phase=OPEN&dateFrom=2026-08-01&dateTo=2026-08-31&sort=createdAtAsc&page=2&pageSize=30"))).toEqual({
      phase: "OPEN",
      dateFrom: "2026-08-01",
      dateTo: "2026-08-31",
      sort: "createdAtAsc",
      page: 2,
      pageSize: 30,
    });
  });

  it("uses safe defaults for invalid values", () => {
    expect(readAdminTopicFilters(new URLSearchParams("phase=UNKNOWN&sort=random&page=0&pageSize=200"))).toEqual({
      phase: undefined,
      dateFrom: undefined,
      dateTo: undefined,
      sort: "topicDateDesc",
      page: 1,
      pageSize: 100,
    });
  });
});
