// @vitest-environment node

import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("server-only", () => ({}));

import { GET, POST } from "@/app/api/admin/[...path]/route";
import { relayAdminRequest } from "./admin-api-relay";

const origin = "https://admin.example.test";
const upstream = "https://backend.example.test/api/v1/admin";
const adminId = "00000000-0000-4000-8000-000000000001";
const resourceId = "00000000-0000-4000-8000-000000000002";
const token = "syntheticHeader.syntheticPayload.syntheticSignature";
const cookieName = "chalkak_admin_session";
const credentials = { username: "test-admin", password: "synthetic-password" };
const loginPayload = { adminId, username: "test-admin", accessToken: token, expiresIn: 3600 };
const fetchMock = vi.fn<typeof fetch>();

function request(path: string, options: {
  method?: string; body?: unknown; cookie?: string;
  headers?: Record<string, string>; omitOrigin?: boolean;
} = {}) {
  const method = options.method ?? "GET";
  const headers = new Headers(options.headers);
  if (method !== "GET" && !options.omitOrigin && !headers.has("Origin")) headers.set("Origin", origin);
  if (options.cookie !== undefined) headers.set("Cookie", `${cookieName}=${options.cookie}`);
  if (options.body !== undefined && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");
  return new NextRequest(`${origin}/api/admin/${path}`, {
    method, headers, body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

function relay(path: string, options: Parameters<typeof request>[1] = {}) {
  return relayAdminRequest(request(path, options), path.split("?")[0].split("/"));
}

function expectClearedCookie(response: Response) {
  expect(response.headers.get("Set-Cookie")).toContain(`${cookieName}=;`);
  expect(response.headers.get("Set-Cookie")).toContain("Max-Age=0");
  expect(response.headers.get("Set-Cookie")).toContain("Path=/api/admin");
}

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", upstream);
  vi.stubEnv("NODE_ENV", "test");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

describe("admin login relay", () => {
  it("awaits route params and returns only public identity and effective expiry", async () => {
    fetchMock.mockResolvedValue(Response.json(loginPayload));

    const response = await POST(request("auth/login", {
      method: "POST", body: credentials, headers: { Authorization: "Bearer browser-value" },
    }), { params: Promise.resolve({ path: ["auth", "login"] }) });

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ adminId, username: "test-admin", expiresIn: 3600 });
    expect(fetchMock).toHaveBeenCalledWith(`${upstream}/auth/login`, expect.objectContaining({
      method: "POST", body: JSON.stringify(credentials), cache: "no-store", redirect: "error",
    }));
    const headers = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(headers.get("Authorization")).toBeNull();
    expect(headers.get("Cookie")).toBeNull();
    const cookie = response.headers.get("Set-Cookie");
    for (const flag of [`${cookieName}=${token}`, "HttpOnly", "SameSite=lax", "Path=/api/admin", "Max-Age=3600"]) {
      expect(cookie).toContain(flag);
    }
    expect(cookie).not.toContain("Domain=");
    expect(cookie).not.toContain("Secure");
    expect(response.headers.get("Cache-Control")).toContain("no-store");
  });

  it("sets Secure on the production cookie", async () => {
    vi.stubEnv("NODE_ENV", "production");
    fetchMock.mockResolvedValue(Response.json(loginPayload));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.headers.get("Set-Cookie")).toContain("Secure");
  });

  it("preserves UTF-8 BOM handling for JSON requests and responses", async () => {
    fetchMock.mockResolvedValue(new Response(`\uFEFF${JSON.stringify(loginPayload)}`, {
      headers: { "Content-Type": "application/json" },
    }));
    const loginRequest = new NextRequest(`${origin}/api/admin/auth/login`, {
      method: "POST", headers: { Origin: origin, "Content-Type": "application/json" },
      body: `\uFEFF${JSON.stringify(credentials)}`,
    });

    const response = await relayAdminRequest(loginRequest, ["auth", "login"]);

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ adminId, username: "test-admin", expiresIn: 3600 });
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify(credentials));
  });

  it("caps the browser session at seven days without rejecting longer backend expiry", async () => {
    fetchMock.mockResolvedValue(Response.json({ ...loginPayload, expiresIn: 86400 * 30 }));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(200);
    expect((await response.json()).expiresIn).toBe(604800);
    expect(response.headers.get("Set-Cookie")).toContain("Max-Age=604800");
  });

  it("does not create a session on invalid credentials or return arbitrary error fields", async () => {
    const error = { errorCode: "ADMIN_UNAUTHORIZED", message: "로그인 정보를 확인해 주세요." };
    fetchMock.mockResolvedValue(Response.json({ ...error, accessToken: "must-not-return" }, { status: 401 }));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(401);
    expect(await response.json()).toEqual(error);
    expect(response.headers.get("Set-Cookie")).toBeNull();
  });

  it.each([
    { accessToken: "invalid;token" }, { accessToken: "a".repeat(4000) },
    { accessToken: undefined }, { accessToken: null },
    { expiresIn: 0 }, { expiresIn: -1 }, { expiresIn: 1.5 },
    { expiresIn: undefined }, { expiresIn: null }, { expiresIn: "3600" },
    { expiresIn: Number.MAX_SAFE_INTEGER + 1 }, { adminId: "not-a-uuid" }, { username: "" },
  ])("rejects unsafe session fields: %j", async (invalid) => {
    fetchMock.mockResolvedValue(Response.json({ ...loginPayload, ...invalid }));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(502);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_INVALID_RESPONSE", message: "관리자 서버 응답이 올바르지 않습니다.",
    });
  });

  it.each([
    ["non-JSON content type", () => new Response(JSON.stringify(loginPayload))],
    ["malformed JSON", () => new Response(`{"accessToken":"${token}",`, {
      headers: { "Content-Type": "application/json" },
    })],
    ["empty JSON body", () => new Response(null, { headers: { "Content-Type": "application/json" } })],
    ["invalid UTF-8", () => new Response(new Uint8Array([0xff]), {
      headers: { "Content-Type": "application/json" },
    })],
    ["oversized response", () => Response.json(loginPayload, { headers: { "Content-Length": "8388609" } })],
    ["missing session body", () => new Response(null, { status: 204 })],
  ] as const)("distinguishes %s from a connection failure without creating a session", async (_label, createResponse) => {
    fetchMock.mockResolvedValue(createResponse());
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(502);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_INVALID_RESPONSE", message: "관리자 서버 응답이 올바르지 않습니다.",
    });
  });

  it.each([500, 502, 503])("distinguishes backend %s from a connection failure without exposing its response", async (status) => {
    fetchMock.mockResolvedValue(Response.json({
      errorCode: "PRIVATE_BACKEND_FAILURE", message: `private failure ${credentials.password}`,
      accessToken: token,
    }, { status }));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(502);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_UPSTREAM_ERROR", message: "관리자 서버에서 요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.",
    });
  });

  it("keeps connection failures distinct and never returns their details", async () => {
    fetchMock.mockRejectedValue(new Error(`private network detail ${credentials.password} ${token}`));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(502);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_API_UNAVAILABLE", message: "관리자 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.",
    });
  });

  it("preserves timeout classification while reading a successful login response", async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation(async (_url, options) => new Response(new ReadableStream({
      start(controller) {
        options?.signal?.addEventListener("abort", () => controller.error(new Error("private body read detail")), { once: true });
      },
    }), { headers: { "Content-Type": "application/json" } }));
    const pending = relay("auth/login", { method: "POST", body: credentials });
    await vi.advanceTimersByTimeAsync(8000);
    const response = await pending;
    expect(response.status).toBe(504);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_API_TIMEOUT", message: "관리자 서버 응답 시간이 초과되었습니다.",
    });
  });

  it.each([
    {}, { username: "", password: "password" }, { username: "admin", password: " " },
    { username: "a".repeat(101), password: "password" },
    { username: "admin", password: "a".repeat(201) }, { ...credentials, accessToken: "injected" },
  ])("rejects invalid credentials locally: %j", async (body) => {
    const response = await relay("auth/login", { method: "POST", body });
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("admin session forwarding", () => {
  it("rejects anonymous requests without an upstream fetch", async () => {
    const response = await GET(request("posts"), { params: Promise.resolve({ path: ["posts"] }) });
    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(response.headers.get("Cache-Control")).toContain("no-store");
  });

  it("ignores a browser Authorization header without the session cookie", async () => {
    const response = await relay("auth/me", { headers: { Authorization: `Bearer ${token}` } });
    expect(response.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects and clears malformed cookies locally", async () => {
    const response = await relay("posts", { cookie: "malformed-token" });
    expect(response.status).toBe(401);
    expectClearedCookie(response);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("forwards only the session bearer and query and never upstream cookies or auth headers", async () => {
    const payload = { posts: [], pageSize: 20 };
    fetchMock.mockResolvedValue(Response.json(payload, { headers: {
      "Set-Cookie": "upstream-secret=hidden", Authorization: `Bearer ${token}`,
    } }));
    const response = await relay("posts?page=0&status=PENDING", {
      cookie: token, headers: { Authorization: "Bearer browser-value", "X-Secret": "ignored" },
    });
    expect(await response.json()).toEqual(payload);
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe(`${upstream}/posts?page=0&status=PENDING`);
    expect(options?.cache).toBe("no-store");
    expect(options?.redirect).toBe("error");
    expect(options?.signal).toBeInstanceOf(AbortSignal);
    expect([...new Headers(options?.headers)]).toEqual([
      ["accept", "application/json"], ["authorization", `Bearer ${token}`],
    ]);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(response.headers.get("Authorization")).toBeNull();
  });

  it("returns identity only from auth/me", async () => {
    fetchMock.mockResolvedValue(Response.json(loginPayload));
    const response = await relay("auth/me", { cookie: token });
    expect(await response.json()).toEqual({ adminId, username: "test-admin" });
  });

  it.each([401, 403])("preserves safe %s errors and clears only rejected authentication", async (status) => {
    const error = { errorCode: "ADMIN_FORBIDDEN", message: "관리자 권한이 필요합니다." };
    fetchMock.mockResolvedValue(Response.json({ ...error, debug: "internal-detail" }, { status }));
    const response = await relay("posts", { cookie: token });
    expect(response.status).toBe(status);
    expect(await response.json()).toEqual(error);
    if (status === 401) expectClearedCookie(response);
    else expect(response.headers.get("Set-Cookie")).toBeNull();
  });

  it("clears rejected authentication even when the backend401 is not JSON", async () => {
    fetchMock.mockResolvedValue(new Response("private stack trace", { status: 401 }));
    const response = await relay("posts", { cookie: token });
    expect(response.status).toBe(401);
    expectClearedCookie(response);
    expect(await response.text()).not.toContain("private stack trace");
  });
});

describe("admin write and path restrictions", () => {
  it.each(["https://untrusted.example.test", "null", "https://admin.example.test.attacker.test"])(
    "rejects cross-origin writes from %s before fetching", async (Origin) => {
      const response = await relay("auth/login", { method: "POST", body: credentials, headers: { Origin } });
      expect(response.status).toBe(403);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it.each(["POST", "PUT", "PATCH", "DELETE"])("rejects %s without Origin", async (method) => {
    const path = method === "PATCH" ? `users/${resourceId}/status`
      : method === "POST" ? "topics" : `topics/${resourceId}`;
    const response = await relay(path, { method, cookie: token, body: {}, omitOrigin: true });
    expect(response.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(["text/plain", "application/x-www-form-urlencoded", "multipart/form-data"])(
    "rejects content type %s", async (contentType) => {
      const response = await relay("auth/login", {
        method: "POST", body: credentials, headers: { "Content-Type": contentType },
      });
      expect(response.status).toBe(415);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("rejects invalid JSON", async () => {
    const response = await relayAdminRequest(new NextRequest(`${origin}/api/admin/auth/login`, {
      method: "POST", headers: { Origin: origin, "Content-Type": "application/json" }, body: "{invalid-json",
    }), ["auth", "login"]);
    expect(response.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("enforces body size even when Content-Length understates actual bytes", async () => {
    const response = await relay("topics", {
      method: "POST", cookie: token, body: { title: "a".repeat(65536) }, headers: { "Content-Length": "1" },
    });
    expect(response.status).toBe(413);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects a slash decoded into a single route segment", async () => {
    const response = await relayAdminRequest(request(`posts%2F${resourceId}`, { cookie: token }), [`posts/${resourceId}`]);
    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([`posts/${resourceId}`, `topics/${resourceId}`])("forwards the required deletion reason for %s", async (path) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const response = await relay(path, { method: "DELETE", cookie: token, body: { reason: "운영 기준 위반" } });
    expect(response.status).toBe(204);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify({ reason: "운영 기준 위반" }));
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Content-Type")).toBe("application/json");
  });

  it.each([
    "dashboard", "pushes", "auth/refresh", "posts/not-a-uuid", "ai-reviews/not-a-uuid", `ai-reviews/${resourceId}/retry`, `posts/${resourceId}/delete`,
    "../users", "posts/../users", "posts/%2e%2e/users", "https://attacker.test", "//attacker.test",
  ])("rejects unknown or traversal path %s", async (path) => {
    const response = await relay(path, { cookie: token });
    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["posts", "POST"], ["auth/login", "GET"], ["auth/me", "POST"],
    ["topics", "PATCH"], ["posts", "HEAD"], ["posts", "OPTIONS"],
    ["feedbacks", "POST"], ["feedbacks", "DELETE"],
    ["ai-prompt-previews", "GET"], ["ai-prompt-versions", "DELETE"],
    [`ai-prompt-versions/${resourceId}`, "PUT"], [`ai-prompt-versions/${resourceId}`, "DELETE"],
    ["ai-reviews", "POST"], [`ai-reviews/${resourceId}`, "PUT"], [`ai-reviews/${resourceId}`, "DELETE"],
  ])("rejects unsupported method on %s: %s", async (path, method) => {
    const response = await relay(path, { method, cookie: token });
    expect(response.status).toBe(405);
    expect(response.headers.get("Allow")).toBeTruthy();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["posts", "GET"], [`posts/${resourceId}`, "GET"], [`posts/${resourceId}`, "DELETE"],
    [`posts/${resourceId}/moderation`, "PUT"], ["users", "GET"], [`users/${resourceId}`, "GET"],
    [`users/${resourceId}/status`, "PATCH"], ["topics", "GET"], ["topics", "POST"],
    [`topics/${resourceId}`, "GET"], [`topics/${resourceId}`, "PUT"], [`topics/${resourceId}`, "DELETE"],
    ["audit-logs", "GET"], ["feedbacks", "GET"],
    ["ai-prompt-previews", "POST"], ["ai-prompt-versions", "GET"], ["ai-prompt-versions", "POST"],
    [`ai-prompt-versions/${resourceId}`, "GET"], ["ai-reviews", "GET"], [`ai-reviews/${resourceId}`, "GET"],
  ])("allows supported path %s: %s", async (path, method) => {
    fetchMock.mockResolvedValue(Response.json({ success: true }));
    const response = await relay(path, { method, cookie: token, body: method === "GET" ? undefined : { reason: "test" } });
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe("AI prompt relay", () => {
  // Over the 64KB default, under the 256KB AI prompt limit.
  const largeBody = { systemPrompt: "가".repeat(30_000) };

  it.each(["ai-prompt-previews", "ai-prompt-versions"])("accepts a body above 64KB on POST %s", async (path) => {
    fetchMock.mockResolvedValue(Response.json({ ok: true }));
    const response = await relay(path, { method: "POST", cookie: token, body: largeBody });
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0][1]?.body).toBe(JSON.stringify(largeBody));
  });

  it.each(["ai-prompt-previews", "ai-prompt-versions"])("still rejects a body above 256KB on POST %s", async (path) => {
    const response = await relay(path, { method: "POST", cookie: token, body: { systemPrompt: "가".repeat(100_000) } });
    expect(response.status).toBe(413);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each(["topics", "banners", "notices"])("keeps the 64KB limit on POST %s", async (path) => {
    const response = await relay(path, { method: "POST", cookie: token, body: largeBody });
    expect(response.status).toBe(413);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("relays version queries and the upstream 400 message", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ promptVersions: [] }));
    const list = await relay("ai-prompt-versions?type=PHOTO_REVIEW&page=1&pageSize=20", { cookie: token });
    expect(await list.json()).toEqual({ promptVersions: [] });
    expect(fetchMock.mock.calls[0][0]).toBe(`${upstream}/ai-prompt-versions?type=PHOTO_REVIEW&page=1&pageSize=20`);
    const error = { errorCode: "BUSINESS_ERROR", message: "허용되지 않은 모델입니다." };
    fetchMock.mockResolvedValueOnce(Response.json(error, { status: 400 }));
    const rejected = await relay("ai-prompt-previews", { method: "POST", cookie: token, body: {} });
    expect(rejected.status).toBe(400);
    expect(await rejected.json()).toEqual(error);
  });

  it("gives only the preview the long upstream timeout", async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url, options) => new Promise<Response>((_resolve, reject) => {
      options?.signal?.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    }));

    const preview = relay("ai-prompt-previews", { method: "POST", cookie: token, body: {} });
    const version = relay("ai-prompt-versions", { method: "POST", cookie: token, body: {} });
    await vi.advanceTimersByTimeAsync(8_000);
    expect((await version).status).toBe(504);
    const [, previewOptions] = fetchMock.mock.calls[0];
    expect(previewOptions?.signal?.aborted).toBe(false);

    await vi.advanceTimersByTimeAsync(61_999);
    expect(previewOptions?.signal?.aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    const response = await preview;
    expect(response.status).toBe(504);
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_API_TIMEOUT", message: "관리자 서버 응답 시간이 초과되었습니다.",
    });
  });
});

describe("upstream safety and logout", () => {
  it.each(["", "not-a-url", "ftp://backend.example.test/api/v1/admin", "https://user:pass@backend.example.test/api/v1/admin", "https://backend.example.test/other"])(
    "rejects invalid fixed backend URL: %s", async (baseUrl) => {
      vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", baseUrl);
      const response = await relay("posts", { cookie: token });
      expect(response.status).toBe(503);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it.each(["localhost", "127.0.0.1", "[::1]"])("permits HTTP only for development loopback %s", async (hostname) => {
    vi.stubEnv("NODE_ENV", "development");
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", `http://${hostname}:8080/api/v1/admin`);
    fetchMock.mockResolvedValue(Response.json({ posts: [] }));
    const response = await relay("posts", { cookie: token });
    expect(response.status).toBe(200);
    expect(fetchMock.mock.calls[0][0]).toBe(`http://${hostname}:8080/api/v1/admin/posts`);
  });

  it.each([
    ["production", "localhost"], ["production", "127.0.0.1"], ["production", "[::1]"],
    ["production", "backend.example.test"], ["development", "backend.example.test"],
    ["development", "localhost.attacker.test"],
  ])("rejects plaintext upstream credentials in %s to %s", async (nodeEnv, hostname) => {
    vi.stubEnv("NODE_ENV", nodeEnv);
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", `http://${hostname}:8080/api/v1/admin`);
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(503);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(response.headers.get("Set-Cookie")).toBeNull();
  });

  it.each([
    () => new Response("private upstream stack trace", { status: 500 }),
    () => Response.json({ errorCode: "DATABASE_FAILED", message: "private upstream stack trace" }, { status: 503 }),
    () => new Response("private upstream stack trace", { status: 200 }),
    () => new Response(null, { status: 302, headers: { Location: "https://untrusted.example.test" } }),
  ])("hides unsuccessful or invalid upstream internals", async (responseFactory) => {
    fetchMock.mockResolvedValue(responseFactory());
    const response = await relay("posts", { cookie: token });
    expect(response.status).toBe(502);
    expect(await response.text()).not.toContain("private upstream stack trace");
    expect(response.headers.get("Location")).toBeNull();
  });

  it("does not return a bearer token echoed in a backend error", async () => {
    fetchMock.mockResolvedValue(Response.json({ errorCode: "ADMIN_FORBIDDEN", message: `Rejected ${token}` }, { status: 403 }));
    const response = await relay("posts", { cookie: token });
    expect(response.status).toBe(403);
    expect(await response.text()).not.toContain(token);
  });

  it("clears the local cookie after confirmed backend logout", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const response = await relay("auth/logout", { method: "POST", cookie: token });
    expect(response.status).toBe(204);
    expect(await response.text()).toBe("");
    expectClearedCookie(response);
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
  });

  it.each([undefined, "0"])("forwards a bodyless logout represented by an empty stream with Content-Length %s", async (contentLength) => {
    const headers = new Headers({ Origin: origin, Cookie: `${cookieName}=${token}` });
    if (contentLength !== undefined) headers.set("Content-Length", contentLength);
    const logoutRequest = new NextRequest(`${origin}/api/admin/auth/logout`, {
      method: "POST", headers,
      body: new ReadableStream({ start(controller) { controller.close(); } }),
    });
    expect(logoutRequest.body).not.toBeNull();
    expect(logoutRequest.headers.has("Content-Type")).toBe(false);
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const response = await relayAdminRequest(logoutRequest, ["auth", "logout"]);

    expect(response.status).toBe(204);
    expectClearedCookie(response);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(fetchMock.mock.calls[0][1]?.body).toBeUndefined();
    const forwardedHeaders = new Headers(fetchMock.mock.calls[0][1]?.headers);
    expect(forwardedHeaders.get("Authorization")).toBe(`Bearer ${token}`);
    expect(forwardedHeaders.has("Content-Type")).toBe(false);
  });

  it.each([
    ["nonempty untyped body", new TextEncoder().encode("{}"), new Headers({ "Content-Length": "0" }), 415],
    ["whitespace-only untyped body", new TextEncoder().encode(" "), new Headers(), 415],
    ["UTF-8 BOM-only body", new Uint8Array([0xef, 0xbb, 0xbf]), new Headers(), 415],
    ["invalid UTF-8 untyped body", new Uint8Array([0xff]), new Headers(), 415],
    ["unsupported content type", new Uint8Array(), new Headers({ "Content-Type": "text/plain" }), 415],
    ["unsupported encoding", new Uint8Array(), new Headers({ "Content-Encoding": "gzip" }), 415],
    ["oversized actual body", new Uint8Array(65537), new Headers({ "Content-Length": "0" }), 413],
    ["oversized declared body", new Uint8Array(), new Headers({ "Content-Length": "65537" }), 413],
  ] as const)("rejects a logout with %s before forwarding", async (_label, bytes, extraHeaders, status) => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const headers = new Headers(extraHeaders);
    headers.set("Origin", origin);
    headers.set("Cookie", `${cookieName}=${token}`);
    const logoutRequest = new NextRequest(`${origin}/api/admin/auth/logout`, {
      method: "POST", headers,
      body: new ReadableStream({ start(controller) { controller.enqueue(bytes); controller.close(); } }),
    });

    const response = await relayAdminRequest(logoutRequest, ["auth", "logout"]);

    expect(response.status).toBe(status);
    expectClearedCookie(response);
    expect(fetchMock).not.toHaveBeenCalled();
    expect((await response.json()).errorCode).toBe(status === 413 ? "ADMIN_BODY_TOO_LARGE" : "ADMIN_JSON_REQUIRED");
  });

  it("rejects a nonempty logout BOM split across stream chunks", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const logoutRequest = new NextRequest(`${origin}/api/admin/auth/logout`, {
      method: "POST", headers: { Origin: origin, Cookie: `${cookieName}=${token}` },
      body: new ReadableStream({
        start(controller) {
          for (const byte of [0xef, 0xbb, 0xbf]) controller.enqueue(new Uint8Array([byte]));
          controller.close();
        },
      }),
    });

    const response = await relayAdminRequest(logoutRequest, ["auth", "logout"]);

    expect(response.status).toBe(415);
    expectClearedCookie(response);
    expect(fetchMock).not.toHaveBeenCalled();
    expect((await response.json()).errorCode).toBe("ADMIN_JSON_REQUIRED");
  });

  it("distinguishes an invalid successful logout response and still clears the local cookie", async () => {
    fetchMock.mockResolvedValue(Response.json({ accessToken: token }));
    const response = await relay("auth/logout", { method: "POST", cookie: token });
    expect(response.status).toBe(502);
    expectClearedCookie(response);
    expect(await response.json()).toEqual({
      errorCode: "ADMIN_INVALID_RESPONSE", message: "관리자 서버 응답이 올바르지 않습니다.",
    });
  });

  it("clears the cookie without claiming backend logout succeeded on network failure", async () => {
    fetchMock.mockRejectedValue(new Error("private network detail"));
    const response = await relay("auth/logout", { method: "POST", cookie: token });
    expect(response.status).toBe(502);
    expectClearedCookie(response);
    expect(await response.text()).not.toContain("private network detail");
  });

  it.each([401, 403, 500])("clears the cookie after a backend logout %s", async (status) => {
    fetchMock.mockResolvedValue(Response.json({ errorCode: "ADMIN_REQUEST_FAILED", message: "요청이 거절되었습니다." }, { status }));
    const response = await relay("auth/logout", { method: "POST", cookie: token });
    expect(response.status).toBe(status >= 500 ? 502 : status);
    expectClearedCookie(response);
  });

  it("clears the cookie even if backend configuration is missing", async () => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "");
    const response = await relay("auth/logout", { method: "POST", cookie: token });
    expect(response.status).toBe(503);
    expectClearedCookie(response);
  });

  it("ends an absent session without fetching on logout", async () => {
    const response = await relay("auth/logout", { method: "POST" });
    expect(response.status).toBe(204);
    expectClearedCookie(response);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("does not clear a session through cross-origin logout", async () => {
    const response = await relay("auth/logout", {
      method: "POST", cookie: token, headers: { Origin: "https://untrusted.example.test" },
    });
    expect(response.status).toBe(403);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("times out backend work and still clears the logout cookie", async () => {
    vi.useFakeTimers();
    fetchMock.mockImplementation((_url, options) => new Promise((_resolve, reject) => {
      options?.signal?.addEventListener("abort", () => reject(new Error("aborted")), { once: true });
    }));
    const pending = relay("auth/logout", { method: "POST", cookie: token });
    await vi.advanceTimersByTimeAsync(8000);
    const response = await pending;
    expect(response.status).toBe(504);
    expectClearedCookie(response);
  });
});

describe("topic push schedule relay", () => {
  it.each(["https://chalkak-dev.pysun.kr/api/v1/admin", "https://production-api.example.test/api/v1/admin"])("forwards CRUD to only the configured backend: %s", async (api) => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", api);
    const schedule = { scheduleId: resourceId, sendTime: "09:00:00", createdAt: "2026-10-08T00:00:00Z", updatedAt: "2026-10-08T00:00:00Z" };
    for (const [path, method, body] of [
      ["topic-push-schedules", "GET", undefined],
      ["topic-push-schedules", "POST", { sendTime: "09:00:00" }],
      [`topic-push-schedules/${resourceId}`, "PUT", { sendTime: "18:30:00" }],
      [`topic-push-schedules/${resourceId}`, "DELETE", undefined],
    ] as const) {
      fetchMock.mockResolvedValueOnce(method === "DELETE" ? new Response(null, { status: 204 }) : Response.json(method === "GET" ? { schedules: [schedule] } : schedule, { status: method === "POST" ? 201 : 200 }));
      const response = await relay(path, { method, body, cookie: token });
      expect(response.status).toBe(method === "DELETE" ? 204 : method === "POST" ? 201 : 200);
      expect(fetchMock).toHaveBeenLastCalledWith(`${api}/${path}`, expect.objectContaining({ method, body: body === undefined ? undefined : JSON.stringify(body) }));
      expect(new Headers(fetchMock.mock.lastCall?.[1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
    }
  });

  it("rejects bodies on schedule deletion while preserving required JSON on other deletions", async () => {
    expect((await relay(`topic-push-schedules/${resourceId}`, { method: "DELETE", body: {}, cookie: token })).status).toBe(400);
    expect((await relay(`topics/${resourceId}`, { method: "DELETE", cookie: token })).status).toBe(415);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("keeps authentication, origin, method, and UUID checks", async () => {
    expect((await relay("topic-push-schedules")).status).toBe(401);
    expect((await relay(`topic-push-schedules/${resourceId}`, { method: "DELETE", cookie: token, headers: { Origin: "https://other.example.test" } })).status).toBe(403);
    expect((await relay("topic-push-schedules", { method: "PUT", body: {}, cookie: token })).status).toBe(405);
    expect((await relay("topic-push-schedules/not-a-uuid", { method: "DELETE", cookie: token })).status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("clears the session on an expired schedule request", async () => {
    fetchMock.mockResolvedValue(Response.json({ errorCode: "UNAUTHORIZED", message: "인증이 만료되었습니다." }, { status: 401 }));
    const response = await relay("topic-push-schedules", { cookie: token });
    expect(response.status).toBe(401);
    expectClearedCookie(response);
  });
});

describe("notice relay", () => {
  it.each([
    ["notices?page=2&pageSize=20", "GET", undefined],
    ["notices", "POST", { title: "공지", body: "본문", scheduledAt: "2099-10-20T00:00:00Z" }],
    [`notices/${resourceId}`, "GET", undefined],
    [`notices/${resourceId}`, "PUT", { title: "수정", body: "본문", scheduledAt: "2099-10-21T00:00:00Z" }],
    [`notices/${resourceId}`, "DELETE", { reason: "일정 취소" }],
  ])("forwards %s %s using the server session", async (path, method, body) => {
    fetchMock.mockResolvedValue(method === "DELETE" ? new Response(null, { status: 204 }) : Response.json({ noticeId: resourceId }));
    const response = await relay(path as string, { method: method as string, body, cookie: token });
    expect(response.status).toBe(method === "DELETE" ? 204 : 200);
    expect(fetchMock).toHaveBeenCalledWith(`${upstream}/${path}`, expect.objectContaining({ method, body: body ? JSON.stringify(body) : undefined }));
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
  });
  it("requires authentication, same-origin mutations, and a reason body for delete", async () => {
    expect((await relay("notices")).status).toBe(401);
    expect((await relay("notices", { method: "POST", cookie: token, omitOrigin: true })).status).toBe(403);
    expect((await relay(`notices/${resourceId}`, { method: "DELETE", cookie: token })).status).toBe(415);
    expect((await relay(`notices/${resourceId}`, { method: "PATCH", cookie: token })).status).toBe(405);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});


describe("reminder relay", () => {
  const body = { title: "리마인더", body: "본문", repeat: "DAILY", target: "TOPIC_NON_PARTICIPANTS", scheduledAt: "2099-10-25T12:00:00Z" };
  it.each([
    ["reminders?page=2&pageSize=20", "GET", undefined, 200],
    ["reminders", "POST", body, 201],
    [`reminders/${resourceId}`, "PUT", body, 200],
    [`reminders/${resourceId}`, "DELETE", undefined, 204],
  ])("forwards %s %s to the admin backend path using the server session", async (path, method, requestBody, status) => {
    fetchMock.mockResolvedValue(status === 204 ? new Response(null, { status }) : Response.json({ reminderId: resourceId }, { status: status as number }));
    const response = await relay(path as string, { method: method as string, body: requestBody, cookie: token });
    expect(response.status).toBe(status);
    expect(fetchMock).toHaveBeenCalledWith(`${upstream}/${path}`, expect.objectContaining({ method, body: requestBody ? JSON.stringify(requestBody) : undefined }));
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
  });
  it("requires authentication and same-origin mutations, and rejects unsupported reminder routes", async () => {
    expect((await relay("reminders")).status).toBe(401);
    expect((await relay("reminders", { method: "POST", cookie: token, omitOrigin: true })).status).toBe(403);
    expect((await relay(`reminders/${resourceId}`, { cookie: token })).status).toBe(405);
    expect((await relay(`reminders/${resourceId}`, { method: "PATCH", cookie: token })).status).toBe(405);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});


describe("banner relay", () => {
  it("routes only banner list GET to the public backend path and forwards session auth", async () => {
    fetchMock.mockResolvedValue(Response.json({ currentPage: 2, pageSize: 20, hasNext: false, banners: [] }));
    const response = await relay("banners?page=2&pageSize=20", { cookie: token });
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledWith("https://backend.example.test/api/v1/banners?page=2&pageSize=20", expect.objectContaining({ method: "GET", cache: "no-store" }));
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
  });
  it("posts banners to the admin backend path", async () => {
    const body = { title: "제목", subtitle: "부제목", webViewUrl: "https://example.com/event", thumbnailUrl: "https://example.com/image.png" };
    fetchMock.mockResolvedValue(Response.json({ id: resourceId, ...body }, { status: 201 }));
    const response = await relay("banners", { method: "POST", body, cookie: token });
    expect(response.status).toBe(201);
    expect(fetchMock).toHaveBeenCalledWith(`${upstream}/banners`, expect.objectContaining({ method: "POST", body: JSON.stringify(body) }));
  });
  it("deletes banners without any upstream body or content type", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    const response = await relay(`banners/${resourceId}`, { method: "DELETE", cookie: token });
    expect(response.status).toBe(204);
    expect(fetchMock).toHaveBeenCalledWith(`${upstream}/banners/${resourceId}`, expect.objectContaining({ method: "DELETE", body: undefined }));
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Content-Type")).toBeNull();
  });
  it("rejects delete bodies before contacting the backend", async () => {
    expect((await relay(`banners/${resourceId}`, { method: "DELETE", body: { reason: "ignored" }, cookie: token })).status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("requires the admin session even for the public upstream list", async () => {
    expect((await relay("banners")).status).toBe(401); expect(fetchMock).not.toHaveBeenCalled();
  });
  it("does not allow detail GET or update PUT", async () => {
    expect((await relay(`banners/${resourceId}`, { cookie: token })).status).toBe(405);
    expect((await relay(`banners/${resourceId}`, { method: "PUT", cookie: token, body: {} })).status).toBe(405);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("app version settings relay", () => {
  it("forwards authenticated GET and platform PUT with the original revision", async () => {
    fetchMock.mockImplementation(async () => Response.json({ appVersions: [] }));
    const list = await relay("app-versions", { cookie: token });
    expect(list.status).toBe(200);
    expect(fetchMock.mock.calls[0][0]).toBe(`${upstream}/app-versions`);
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get("Authorization")).toBe(`Bearer ${token}`);
    const body = { revision: null, minimumSupportedVersion: "1.10.0", storeUrl: "https://apps.apple.com/app/id123456789", reason: "스토어 확인" };
    for (const platform of ["ANDROID", "IOS"]) {
      const response = await relay(`app-versions/${platform}`, { method: "PUT", cookie: token, body });
      expect(response.status).toBe(200);
      expect(fetchMock).toHaveBeenLastCalledWith(`${upstream}/app-versions/${platform}`, expect.objectContaining({ method: "PUT", body: JSON.stringify(body), cache: "no-store" }));
    }
  });
  it("rejects missing sessions, invalid platforms, unsupported verbs, and foreign origins", async () => {
    expect((await relay("app-versions")).status).toBe(401);
    expect((await relay("app-versions/IOS", { method: "PUT", body: {} })).status).toBe(401);
    expect((await relay("app-versions/android", { method: "PUT", cookie: token, body: {} })).status).toBe(404);
    expect((await relay("app-versions/WINDOWS", { method: "PUT", cookie: token, body: {} })).status).toBe(404);
    expect((await relay("app-versions/IOS", { cookie: token })).status).toBe(405);
    expect((await relay("app-versions", { method: "POST", cookie: token, body: {} })).status).toBe(405);
    expect((await relay("app-versions/IOS", { method: "PUT", cookie: token, body: {}, headers: { Origin: "https://foreign.example.test" } })).status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("preserves the conflict code for UI recovery", async () => {
    fetchMock.mockResolvedValue(Response.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "다른 관리자가 변경했습니다." }, { status: 400 }));
    const response = await relay("app-versions/ANDROID", { method: "PUT", cookie: token, body: {} });
    expect(response.status).toBe(400);
    expect(await response.json()).toMatchObject({ errorCode: "RESOURCE_STATE_CHANGED" });
  });
});
