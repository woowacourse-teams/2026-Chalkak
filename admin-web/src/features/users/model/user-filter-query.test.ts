import { describe, expect, it } from "vitest";

import { readAdminUserFilters } from "./user-filter-query";

describe("readAdminUserFilters", () => {
  it("accepts supported URL filters", () => {
    expect(readAdminUserFilters(new URLSearchParams("email=test%40example.com&status=BANNED&sort=createdAtAsc&page=2&pageSize=50"))).toEqual({
      email: "test@example.com",
      status: "BANNED",
      sort: "createdAtAsc",
      page: 2,
      pageSize: 50,
    });
  });

  it("falls back safely for invalid values", () => {
    expect(readAdminUserFilters(new URLSearchParams("status=UNKNOWN&page=-1&pageSize=500"))).toEqual({
      email: undefined,
      status: "ACTIVE",
      sort: "createdAtDesc",
      page: 1,
      pageSize: 100,
    });
  });
});
