import { describe, expect, it } from "vitest";

import {
  readAdminPostFilters,
  withQueryPatch,
} from "./post-filter-query";

describe("post filter URL query", () => {
  it("reads valid filters and applies safe defaults", () => {
    const filters = readAdminPostFilters(
      new URLSearchParams(
        "status=PENDING&page=2&pageSize=50&sort=createdAtAsc",
      ),
    );

    expect(filters).toMatchObject({
      status: "PENDING",
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

  it("reads Instagram consent and ID filters", () => {
    expect(
      readAdminPostFilters(new URLSearchParams("instagramConsent=revoked&instagramId=%20%40Chalkak%20")),
    ).toMatchObject({ instagramConsent: "revoked", instagramId: "@Chalkak" });
    expect(
      readAdminPostFilters(new URLSearchParams("instagramConsent=maybe&instagramId=%20")),
    ).toMatchObject({ instagramConsent: undefined, instagramId: undefined });
  });

  it("allows popularity sort only with a topic", () => {
    expect(readAdminPostFilters(new URLSearchParams("sort=likeCountDesc&topicId=t1")).sort).toBe("likeCountDesc");
    expect(readAdminPostFilters(new URLSearchParams("sort=likeCountDesc")).sort).toBe("createdAtDesc");
  });

  it("ignores removed date filters from old bookmarks", () => {
    const filters = readAdminPostFilters(
      new URLSearchParams("topicDate=2026-08-28&createdAtFrom=2026-08-01T00:00:00Z&createdAtTo=2026-08-31T00:00:00Z"),
    );
    expect(filters).not.toHaveProperty("topicDate");
    expect(filters).not.toHaveProperty("createdAtFrom");
    expect(filters).not.toHaveProperty("createdAtTo");
  });
});
