import { describe, expect, it } from "vitest";

import {
  dateEndInstant,
  dateStartInstant,
  readAdminPostFilters,
  withQueryPatch,
} from "./post-filter-query";

describe("post filter URL query", () => {
  it("reads valid filters and applies safe defaults", () => {
    const filters = readAdminPostFilters(
      new URLSearchParams(
        "status=PENDING&topicDate=2026-08-28&page=2&pageSize=50&sort=createdAtAsc",
      ),
    );

    expect(filters).toMatchObject({
      status: "PENDING",
      topicDate: "2026-08-28",
      page: 2,
      pageSize: 50,
      sort: "createdAtAsc",
    });
  });

  it("drops invalid values instead of forwarding them to the API", () => {
    const filters = readAdminPostFilters(
      new URLSearchParams("status=UNKNOWN&page=-1&pageSize=500&sort=random"),
    );

    expect(filters).toMatchObject({
      status: undefined,
      page: 1,
      pageSize: 100,
      sort: "createdAtDesc",
    });
  });

  it("preserves current filters while changing the page", () => {
    expect(
      withQueryPatch(new URLSearchParams("status=PENDING&page=1"), { page: 3 }),
    ).toBe("?status=PENDING&page=3");
  });

  it("converts date filters to inclusive UTC Instant bounds", () => {
    expect(dateStartInstant("2026-08-01")).toBe("2026-08-01T00:00:00Z");
    expect(dateEndInstant("2026-08-31")).toBe("2026-08-31T23:59:59Z");
  });
});
