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
    { expiresIn: 0 }, { expiresIn: -1 }, { expiresIn: 1.5 },
    { expiresIn: Number.MAX_SAFE_INTEGER + 1 }, { adminId: "not-a-uuid" }, { username: "" },
  ])("rejects unsafe session fields: %j", async (invalid) => {
    fetchMock.mockResolvedValue(Response.json({ ...loginPayload, ...invalid }));
    const response = await relay("auth/login", { method: "POST", body: credentials });
    expect(response.status).toBe(502);
    expect(response.headers.get("Set-Cookie")).toBeNull();
    expect(await response.text()).not.toContain(token);
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
    "dashboard", "pushes", "auth/refresh", "posts/not-a-uuid", `posts/${resourceId}/delete`,
    "../users", "posts/../users", "posts/%2e%2e/users", "https://attacker.test", "//attacker.test",
  ])("rejects unknown or traversal path %s", async (path) => {
    const response = await relay(path, { cookie: token });
    expect(response.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it.each([
    ["posts", "POST"], ["auth/login", "GET"], ["auth/me", "POST"],
    ["topics", "PATCH"], ["posts", "HEAD"], ["posts", "OPTIONS"],
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
    ["audit-logs", "GET"],
  ])("allows supported path %s: %s", async (path, method) => {
    fetchMock.mockResolvedValue(Response.json({ success: true }));
    const response = await relay(path, { method, cookie: token, body: method === "GET" ? undefined : { reason: "test" } });
    expect(response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledOnce();
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
