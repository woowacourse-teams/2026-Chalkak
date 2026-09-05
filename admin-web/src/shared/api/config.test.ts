import { describe, expect, it } from "vitest";

import { assertMockModeAllowed, readPublicApiConfig } from "./config";

describe("public API configuration", () => {
  it("fails clearly when the API base URL is missing", () => {
    expect(() =>
      readPublicApiConfig({
        NODE_ENV: "development",
        NEXT_PUBLIC_API_MODE: "real",
      }),
    ).toThrow("NEXT_PUBLIC_ADMIN_API_BASE_URL이 없습니다.");
  });

  it("normalizes the API base URL", () => {
    expect(
      readPublicApiConfig({
        NODE_ENV: "development",
        NEXT_PUBLIC_API_MODE: "mock",
        NEXT_PUBLIC_ADMIN_API_BASE_URL:
          "http://localhost:8080/api/v1/admin/",
      }),
    ).toEqual({
      baseUrl: "http://localhost:8080/api/v1/admin",
      mode: "mock",
      timeoutMs: 10_000,
    });
  });

  it("rejects Mock mode in production", () => {
    expect(() => assertMockModeAllowed("mock", "production")).toThrow(
      "운영 환경에서는 Mock API 모드를 사용할 수 없습니다.",
    );
  });
});
