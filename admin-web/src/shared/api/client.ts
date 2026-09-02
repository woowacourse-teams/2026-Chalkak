import type { ApiErrorResponse } from "./contracts";
import { readPublicApiConfig, type PublicApiConfig } from "./config";
import { ApiError } from "./errors";
import { notifyAdminSessionExpired } from "./session-events";

type ApiRequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
};

const activeRequests = new Set<AbortController>();
let sessionGeneration = 0;

export function cancelAdminRequests() {
  sessionGeneration += 1;
  for (const controller of activeRequests) controller.abort("session-change");
  activeRequests.clear();
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  if (typeof value !== "object" || value === null) {
    return false;
  }
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.errorCode === "string" &&
    typeof candidate.message === "string"
  );
}

async function parseJson(response: Response): Promise<unknown> {
  const text = await response.text();
  if (!text) {
    return undefined;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch (cause) {
    throw new ApiError({
      kind: "invalid-response",
      status: response.status,
      message: "서버 응답을 JSON으로 해석할 수 없습니다.",
      cause,
    });
  }
}

export class ApiClient {
  constructor(private readonly config: PublicApiConfig) {}

  async request<T>(
    path: string,
    options: ApiRequestOptions = {},
  ): Promise<T> {
    const controller = new AbortController();
    activeRequests.add(controller);
    const requestGeneration = sessionGeneration;
    const timeoutId = window.setTimeout(
      () => controller.abort("timeout"),
      this.config.timeoutMs,
    );
    const externalSignal = options.signal;
    const abortFromExternalSignal = () => controller.abort(externalSignal?.reason);
    if (externalSignal?.aborted) {
      abortFromExternalSignal();
    }
    externalSignal?.addEventListener("abort", abortFromExternalSignal, {
      once: true,
    });

    const headers = new Headers(options.headers);
    headers.set("Accept", "application/json");
    if (options.body !== undefined) {
      headers.set("Content-Type", "application/json");
    }

    try {
      // The real session cookie is HttpOnly; only our server reads and forwards it.
      const baseUrl = this.config.mode === "real" ? "/api/admin" : this.config.baseUrl;
      const response = await fetch(baseUrl + path, {
        ...options,
        credentials: "same-origin",
        cache: "no-store",
        body:
          options.body === undefined ? undefined : JSON.stringify(options.body),
        headers,
        signal: controller.signal,
      });
      const payload = await parseJson(response);
      if (response.status === 401 && !path.startsWith("/auth/") && requestGeneration === sessionGeneration) {
        notifyAdminSessionExpired();
      }

      if (!response.ok) {
        if (isApiErrorResponse(payload)) {
          throw new ApiError({
            kind: "api",
            status: response.status,
            errorCode: payload.errorCode,
            message: payload.message,
          });
        }
        throw new ApiError({
          kind: "api",
          status: response.status,
          message: "요청을 처리하지 못했습니다.",
        });
      }

      return payload as T;
    } catch (error) {
      if (error instanceof ApiError) {
        throw error;
      }
      if (controller.signal.reason === "timeout") {
        throw new ApiError({
          kind: "timeout",
          message: "요청 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.",
          cause: error,
        });
      }
      throw new ApiError({
        kind: "network",
        message: "서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.",
        cause: error,
      });
    } finally {
      activeRequests.delete(controller);
      window.clearTimeout(timeoutId);
      externalSignal?.removeEventListener("abort", abortFromExternalSignal);
    }
  }
}

export function createApiClient(config: PublicApiConfig) {
  return new ApiClient(config);
}

export function getApiClient() {
  return createApiClient(readPublicApiConfig());
}
