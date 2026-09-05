import { describe, expect, it } from "vitest";

import { getLoginUrl, getSafeReturnTo } from "./return-to";

describe("admin return path", () => {
  it.each([
    "https://example.com/posts", "//example.com/posts", "javascript:alert(1)",
    "/\\example.com", "/%5cexample.com", "/%2fexample.com", "/posts\n",
    "/login?returnTo=/login", "/api/admin/auth/logout", "/posts/../../login", "%2Fposts", "/%",
  ])("rejects unsafe or non-screen paths: %s", (path) => {
    expect(getSafeReturnTo(path)).toBe("/");
  });

  it("keeps an internal post deep link and list filters", () => {
    const path = "/posts/11111111-1111-4111-8111-111111111111?returnTo=%2Fposts%3Fstatus%3DPENDING";
    expect(getSafeReturnTo(path)).toBe(path);
    expect(getLoginUrl(path)).toBe("/login?" + new URLSearchParams({ returnTo: path }));
  });

  it("defaults to home for missing paths", () => {
    expect(getSafeReturnTo(null)).toBe("/");
    expect(getLoginUrl("/")).toBe("/login");
  });
});
