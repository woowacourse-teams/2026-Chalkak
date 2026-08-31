import { afterEach, describe, expect, it, vi } from "vitest";

import type { AdminPostListResponse } from "./contracts";
import { cancelAdminRequests, createApiClient } from "./client";
import { ApiError } from "./errors";
import { ADMIN_SESSION_EXPIRED_EVENT } from "./session-events";

const config = {
  baseUrl: "http://localhost:8080/api/v1/admin",
  mode: "mock",
  timeoutMs: 1_000,
} as const;

afterEach(() => {
  cancelAdminRequests();
  vi.restoreAllMocks();
});

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
      errorCode: "FORBIDDEN",
      message: "관리자 API에 접근할 수 없습니다.",
    } satisfies Partial<ApiError>);
  });

  it("does not let an old request's delayed 401 expire the new session", async () => {
    let resolveOldResponse!: (response: Response) => void;
    const oldResponse = new Promise<Response>((resolve) => { resolveOldResponse = resolve; });
    const fetchSpy = vi.spyOn(globalThis, "fetch")
      // Deliberately finish after abort to exercise the generation check as well.
      .mockImplementationOnce(() => oldResponse)
      .mockResolvedValueOnce(Response.json({ posts: [], session: "new" }));
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");
    const client = createApiClient(config);
    const pendingOldRequest = client.request("/posts").catch((error: unknown) => error);
    const oldSignal = fetchSpy.mock.calls[0][1]?.signal;

    cancelAdminRequests();
    expect(oldSignal?.aborted).toBe(true);
    expect(oldSignal?.reason).toBe("session-change");
    await expect(client.request("/posts")).resolves.toEqual({ posts: [], session: "new" });
    resolveOldResponse(Response.json({ errorCode: "UNAUTHORIZED", message: "이전 세션이 만료되었습니다." }, { status: 401 }));

    expect(await pendingOldRequest).toMatchObject({ name: "ApiError", status: 401 });
    expect(dispatchSpy.mock.calls.filter(([event]) => event.type === ADMIN_SESSION_EXPIRED_EVENT)).toHaveLength(0);
  });

  it("still expires the current session on its own protected 401", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({
      errorCode: "UNAUTHORIZED", message: "로그인이 필요합니다.",
    }, { status: 401 }));
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");

    await expect(createApiClient(config).request("/posts")).rejects.toMatchObject({ status: 401 });

    expect(dispatchSpy.mock.calls.filter(([event]) => event.type === ADMIN_SESSION_EXPIRED_EVENT)).toHaveLength(1);
  });

  it("does not announce expired authentication for a failed login", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(Response.json({
      errorCode: "UNAUTHORIZED", message: "로그인 정보를 확인해 주세요.",
    }, { status: 401 }));
    const dispatchSpy = vi.spyOn(window, "dispatchEvent");

    await expect(createApiClient(config).request("/auth/login", {
      method: "POST", body: { username: "synthetic-admin", password: "synthetic-password" },
    })).rejects.toMatchObject({ status: 401 });

    expect(dispatchSpy.mock.calls.filter(([event]) => event.type === ADMIN_SESSION_EXPIRED_EVENT)).toHaveLength(0);
  });
});
