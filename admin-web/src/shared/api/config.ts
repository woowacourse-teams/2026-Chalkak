import { ApiError } from "./errors";

export type ApiMode = "mock" | "real";

export interface PublicApiConfig {
  baseUrl: string;
  mode: ApiMode;
  timeoutMs: number;
}

export const DEFAULT_API_TIMEOUT_MS = 10_000;

export function resolveApiMode(value = process.env.NEXT_PUBLIC_API_MODE): ApiMode {
  if (value === undefined || value === "" || value === "real") {
    return "real";
  }
  if (value === "mock") {
    return "mock";
  }
  throw new ApiError({
    kind: "configuration",
    message: "NEXT_PUBLIC_API_MODE는 mock 또는 real이어야 합니다.",
  });
}

export function assertMockModeAllowed(
  mode: ApiMode,
  nodeEnv = process.env.NODE_ENV,
) {
  if (nodeEnv === "production" && mode === "mock") {
    throw new ApiError({
      kind: "configuration",
      message: "운영 환경에서는 Mock API 모드를 사용할 수 없습니다.",
    });
  }
}

export function readPublicApiConfig(
  env: NodeJS.ProcessEnv = process.env,
): PublicApiConfig {
  const mode = resolveApiMode(env.NEXT_PUBLIC_API_MODE);
  assertMockModeAllowed(mode, env.NODE_ENV);

  const rawBaseUrl = env.NEXT_PUBLIC_ADMIN_API_BASE_URL?.trim();
  if (!rawBaseUrl) {
    throw new ApiError({
      kind: "configuration",
      message:
        "NEXT_PUBLIC_ADMIN_API_BASE_URL이 없습니다. .env.local 설정을 확인해 주세요.",
    });
  }

  let url: URL;
  try {
    url = new URL(rawBaseUrl);
  } catch (cause) {
    throw new ApiError({
      kind: "configuration",
      message: "관리자 API 주소가 올바른 URL이 아닙니다.",
      cause,
    });
  }

  if (url.protocol !== "http:" && url.protocol !== "https:") {
    throw new ApiError({
      kind: "configuration",
      message: "관리자 API 주소는 HTTP 또는 HTTPS URL이어야 합니다.",
    });
  }

  return {
    baseUrl: rawBaseUrl.replace(/\/+$/, ""),
    mode,
    timeoutMs: DEFAULT_API_TIMEOUT_MS,
  };
}
