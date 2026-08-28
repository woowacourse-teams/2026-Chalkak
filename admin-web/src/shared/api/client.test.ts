import { describe, expect, it } from "vitest";

import type { AdminPostListResponse } from "./contracts";
import { createApiClient } from "./client";
import { ApiError } from "./errors";

const config = {
  baseUrl: "http://localhost:8080/api/v1/admin",
  mode: "mock",
  timeoutMs: 1_000,
} as const;

describe("ApiClient", () => {
  it("parses a successful JSON response", async () => {
    const client = createApiClient(config);

    const response = await client.request<AdminPostListResponse>("/posts");

    expect(response.posts).not.toHaveLength(0);
    expect(response.posts[0]?.createdAt).toMatch(
      /^\d{4}-\d{2}-\d{2}T.*Z$/,
    );
  });

  it("maps the common error response to a UI-safe ApiError", async () => {
    const client = createApiClient(config);

    await expect(
      client.request("/posts?scenario=forbidden"),
    ).rejects.toMatchObject({
      name: "ApiError",
      kind: "api",
      status: 403,
      errorCode: "ADMIN_FORBIDDEN",
      message: "관리자 API에 접근할 수 없습니다.",
    } satisfies Partial<ApiError>);
  });
});
