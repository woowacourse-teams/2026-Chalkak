import { describe, expect, it } from "vitest";

import {
  dateEndInstant,
  dateStartInstant,
  instantDate,
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

  it("falls back to the actionable pending queue for invalid values", () => {
    const filters = readAdminPostFilters(
      new URLSearchParams("status=UNKNOWN&page=-1&pageSize=500&sort=random"),
    );

    expect(filters).toMatchObject({
      status: "PENDING",
      page: 1,
      pageSize: 100,
      sort: "createdAtDesc",
    });
  });

  it("does not expose image-validation work to administrators", () => {
    expect(
      readAdminPostFilters(new URLSearchParams("status=VALIDATING")).status,
    ).toBe("PENDING");
    expect(readAdminPostFilters(new URLSearchParams()).status).toBe("PENDING");
  });

  it("preserves current filters while changing the page", () => {
    expect(
      withQueryPatch(new URLSearchParams("status=PENDING&page=1"), { page: 3 }),
    ).toBe("?status=PENDING&page=3");
  });

  it("converts Korean calendar dates to inclusive Instant bounds", () => {
    expect(dateStartInstant("2026-08-01")).toBe("2026-07-31T15:00:00.000Z");
    expect(dateEndInstant("2026-08-31")).toBe("2026-08-31T14:59:59.999Z");
    expect(instantDate("2026-07-31T15:00:00.000Z")).toBe("2026-08-01");
    expect(instantDate("invalid")).toBe("");
  });
});
