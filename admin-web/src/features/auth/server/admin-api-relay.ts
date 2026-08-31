import "server-only";

import { NextRequest, NextResponse } from "next/server";

const SESSION_COOKIE = "chalkak_admin_session";
const COOKIE_PATH = "/api/admin";
const MAX_BODY_BYTES = 64 * 1024;
const MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
// Leave time for the browser's 10-second timeout to receive errors and cookie deletion.
const UPSTREAM_TIMEOUT_MS = 8_000;
// A browser session may be shorter, but never longer, than the backend token.
const MAX_SESSION_SECONDS = 7 * 24 * 60 * 60;
const UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
const UUID_PATTERN = new RegExp(`^${UUID}$`);
const ROUTES = [
  { pattern: /^\/auth\/login$/, methods: ["POST"] },
  { pattern: /^\/auth\/me$/, methods: ["GET"] },
  { pattern: /^\/auth\/logout$/, methods: ["POST"] },
  { pattern: /^\/posts$/, methods: ["GET"] },
  { pattern: new RegExp(`^/posts/${UUID}$`), methods: ["GET", "DELETE"] },
  { pattern: new RegExp(`^/posts/${UUID}/moderation$`), methods: ["PUT"] },
  { pattern: /^\/users$/, methods: ["GET"] },
  { pattern: new RegExp(`^/users/${UUID}$`), methods: ["GET"] },
  { pattern: new RegExp(`^/users/${UUID}/status$`), methods: ["PATCH"] },
  { pattern: /^\/topics$/, methods: ["GET", "POST"] },
  { pattern: new RegExp(`^/topics/${UUID}$`), methods: ["GET", "PUT", "DELETE"] },
  { pattern: /^\/audit-logs$/, methods: ["GET"] },
];

class RelayError extends Error {
  constructor(readonly status: number, readonly errorCode: string, message: string) {
    super(message);
  }
}

function json(payload: unknown, status = 200) {
  return NextResponse.json(payload, {
    status,
    headers: { "Cache-Control": "private, no-store", "X-Content-Type-Options": "nosniff" },
  });
}

function empty() {
  return new NextResponse(null, { status: 204, headers: { "Cache-Control": "private, no-store" } });
}

function errorResponse(status: number, errorCode: string, message: string) {
  return json({ errorCode, message }, status);
}

function cookieOptions(maxAge: number) {
  return {
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: COOKIE_PATH,
    maxAge,
  };
}

function clearSession(response: NextResponse) {
  response.cookies.set(SESSION_COOKIE, "", cookieOptions(0));
  return response;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isSessionToken(value: unknown): value is string {
  return typeof value === "string" && value.length <= 3500 &&
    /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(value);
}

function identity(payload: unknown) {
  if (!isRecord(payload) || typeof payload.adminId !== "string" ||
    !UUID_PATTERN.test(payload.adminId) || typeof payload.username !== "string" ||
    !payload.username.trim() || payload.username.length > 100) {
    throw new RelayError(502, "ADMIN_INVALID_RESPONSE", "관리자 서버 응답이 올바르지 않습니다.");
  }
  return { adminId: payload.adminId, username: payload.username };
}

function upstreamUrl(path: string, search: string) {
  let url: URL;
  try {
    url = new URL(process.env.NEXT_PUBLIC_ADMIN_API_BASE_URL?.trim() ?? "");
    const isLocalDevelopmentHttp = url.protocol === "http:" && process.env.NODE_ENV !== "production" &&
      ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if ((url.protocol !== "https:" && !isLocalDevelopmentHttp) || url.username || url.password ||
      url.search || url.hash || !url.pathname.replace(/\/+$/, "").endsWith("/api/v1/admin")) {
      throw new Error("Invalid upstream configuration");
    }
  } catch {
    throw new RelayError(503, "ADMIN_API_UNAVAILABLE", "관리자 API 연결 설정을 확인해 주세요.");
  }
  url.pathname = url.pathname.replace(/\/+$/, "") + path;
  url.search = search;
  return url.toString();
}

async function readBoundedText(source: Request | Response, limit: number) {
  const declaredLength = source.headers.get("Content-Length");
  if (declaredLength !== null && (!/^\d+$/.test(declaredLength) || Number(declaredLength) > limit)) {
    throw new RelayError(413, "ADMIN_BODY_TOO_LARGE", "요청 본문이 너무 큽니다.");
  }
  if (!source.body) return "";
  const reader = source.body.getReader();
  const decoder = new TextDecoder("utf-8", { fatal: true });
  let bytes = 0;
  let text = "";
  try {
    while (true) {
      const chunk = await reader.read();
      if (chunk.done) break;
      bytes += chunk.value.byteLength;
      if (bytes > limit) {
        void reader.cancel().catch(() => {});
        throw new RelayError(413, "ADMIN_BODY_TOO_LARGE", "요청 본문이 너무 큽니다.");
      }
      text += decoder.decode(chunk.value, { stream: true });
    }
    return text + decoder.decode();
  } finally {
    reader.releaseLock();
  }
}

function isJson(contentType: string | null) {
  return contentType?.split(";", 1)[0].trim().toLowerCase() === "application/json";
}

async function requestBody(request: NextRequest, isLogout: boolean, isLogin: boolean) {
  if (request.method === "GET") return undefined;
  if (isLogout && !request.body && !request.headers.has("Content-Type")) return undefined;
  const encoding = request.headers.get("Content-Encoding");
  if (!isJson(request.headers.get("Content-Type")) || (encoding && encoding !== "identity")) {
    throw new RelayError(415, "ADMIN_JSON_REQUIRED", "JSON 형식의 요청만 지원합니다.");
  }
  let body: unknown;
  try {
    body = JSON.parse(await readBoundedText(request, MAX_BODY_BYTES));
  } catch (error) {
    if (error instanceof RelayError) throw error;
    throw new RelayError(400, "ADMIN_INVALID_REQUEST", "요청 본문이 올바른 JSON이 아닙니다.");
  }
  if (!isRecord(body)) {
    throw new RelayError(400, "ADMIN_INVALID_REQUEST", "요청 본문은 JSON 객체여야 합니다.");
  }
  if (isLogin && (Object.keys(body).some((key) => key !== "username" && key !== "password") ||
    typeof body.username !== "string" || !body.username.trim() || body.username.length > 100 ||
    typeof body.password !== "string" || !body.password.trim() || body.password.length > 200)) {
    throw new RelayError(400, "ADMIN_INVALID_REQUEST", "관리자 아이디와 비밀번호를 확인해 주세요.");
  }
  return body;
}

async function upstreamError(response: Response, secrets: string[]) {
  let payload: unknown;
  try {
    if (isJson(response.headers.get("Content-Type"))) {
      payload = JSON.parse(await readBoundedText(response, MAX_BODY_BYTES));
    } else {
      void response.body?.cancel().catch(() => {});
    }
  } catch { /* Untrusted error bodies are never returned verbatim. */ }
  const message = isRecord(payload) ? payload.message : undefined;
  if (isRecord(payload) && typeof payload.errorCode === "string" &&
    /^[A-Z][A-Z0-9_]{0,99}$/.test(payload.errorCode) && typeof message === "string" &&
    message.length > 0 && message.length <= 500 &&
    !/[\u0000-\u001f\u007f]/.test(message) &&
    !/\bbearer\s/i.test(message) &&
    !secrets.some((secret) => message.includes(secret))) {
    return errorResponse(response.status, payload.errorCode, message);
  }
  return errorResponse(response.status, "ADMIN_REQUEST_FAILED", "관리자 요청을 처리하지 못했습니다.");
}

async function forward(request: NextRequest, path: string, token: string | undefined) {
  const isLogin = path === "/auth/login";
  const isLogout = path === "/auth/logout";
  const body = await requestBody(request, isLogout, isLogin);
  const url = upstreamUrl(path, request.nextUrl.search);
  const headers = new Headers({ Accept: "application/json" });
  if (body !== undefined) headers.set("Content-Type", "application/json");
  if (!isLogin && token) headers.set("Authorization", `Bearer ${token}`);
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
  try {
    const response = await fetch(url, {
      method: request.method, headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: "no-store", redirect: "error", signal: controller.signal,
    });
    if (response.status >= 400 && response.status < 500) {
      const secrets = [token, isLogin && typeof body?.password === "string" ? body.password : undefined]
        .filter((value): value is string => Boolean(value));
      return await upstreamError(response, secrets);
    }
    if (!response.ok) {
      void response.body?.cancel().catch(() => {});
      throw new Error("Upstream request failed");
    }
    if (isLogout) {
      if (response.status !== 204) throw new Error("Invalid logout response");
      return empty();
    }
    if (response.status === 204 && !isLogin && path !== "/auth/me") return empty();
    if (!isJson(response.headers.get("Content-Type"))) {
      void response.body?.cancel().catch(() => {});
      throw new Error("Invalid upstream content type");
    }
    const payload: unknown = JSON.parse(await readBoundedText(response, MAX_RESPONSE_BYTES));
    if (isLogin) {
      const admin = identity(payload);
      if (!isRecord(payload) || !isSessionToken(payload.accessToken) ||
        typeof payload.expiresIn !== "number" || !Number.isSafeInteger(payload.expiresIn) || payload.expiresIn <= 0) {
        throw new Error("Invalid upstream session");
      }
      const expiresIn = Math.min(payload.expiresIn, MAX_SESSION_SECONDS);
      const result = json({ ...admin, expiresIn });
      result.cookies.set(SESSION_COOKIE, payload.accessToken, cookieOptions(expiresIn));
      return result;
    }
    return json(path === "/auth/me" ? identity(payload) : payload, response.status);
  } catch (error) {
    if (controller.signal.aborted) {
      throw new RelayError(504, "ADMIN_API_TIMEOUT", "관리자 서버 응답 시간이 초과되었습니다.");
    }
    if (error instanceof RelayError && error.status === 502) throw error;
    throw new RelayError(502, "ADMIN_API_UNAVAILABLE", "관리자 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
  } finally {
    clearTimeout(timeout);
  }
}

export async function relayAdminRequest(request: NextRequest, segments: string[]) {
  const path = "/" + segments.join("/");
  const route = segments.every((segment) => /^[A-Za-z0-9-]+$/.test(segment))
    ? ROUTES.find(({ pattern }) => pattern.test(path)) : undefined;
  if (!route) return errorResponse(404, "ADMIN_ROUTE_NOT_FOUND", "지원하지 않는 관리자 API입니다.");
  if (!route.methods.includes(request.method)) {
    const response = errorResponse(405, "ADMIN_METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다.");
    response.headers.set("Allow", route.methods.join(", "));
    return response;
  }
  if (request.method !== "GET" && request.headers.get("Origin") !== request.nextUrl.origin) {
    return errorResponse(403, "ADMIN_ORIGIN_FORBIDDEN", "같은 출처의 관리자 요청만 허용됩니다.");
  }
  const cookie = request.cookies.get(SESSION_COOKIE)?.value;
  const isLogout = path === "/auth/logout";
  if (path !== "/auth/login" && !isSessionToken(cookie)) {
    const response = isLogout ? empty() : errorResponse(401, "ADMIN_UNAUTHORIZED", "관리자 로그인이 필요합니다.");
    return cookie !== undefined || isLogout ? clearSession(response) : response;
  }
  let response: NextResponse;
  try {
    response = await forward(request, path, cookie);
  } catch (error) {
    response = error instanceof RelayError
      ? errorResponse(error.status, error.errorCode, error.message)
      : errorResponse(502, "ADMIN_API_UNAVAILABLE", "관리자 요청을 처리하지 못했습니다.");
  }
  return isLogout || (response.status === 401 && cookie !== undefined) ? clearSession(response) : response;
}
