// @vitest-environment node

import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const apiUrl = "https://admin-api.example.invalid/api/v1/admin";
const urlError = "배포 빌드의 NEXT_PUBLIC_ADMIN_API_BASE_URL은 인증 정보, 쿼리, 프래그먼트 없이 /api/v1/admin으로 끝나는 HTTPS URL이어야 합니다.";

function loadNextConfig() {
  return import("../../../next.config");
}

beforeEach(() => {
  vi.resetModules();
  vi.stubEnv("NODE_ENV", "development");
  vi.stubEnv("VERCEL_ENV", undefined);
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "real");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", apiUrl);
});

afterEach(() => vi.unstubAllEnvs());

describe.each([
  { label: "Vercel Preview", nodeEnv: "development", vercelEnv: "preview" },
  { label: "Vercel Production", nodeEnv: "development", vercelEnv: "production" },
  { label: "production without Vercel metadata", nodeEnv: "production", vercelEnv: undefined },
])("deployment configuration: $label", ({ nodeEnv, vercelEnv }) => {
  beforeEach(() => {
    vi.stubEnv("NODE_ENV", nodeEnv);
    vi.stubEnv("VERCEL_ENV", vercelEnv);
  });

  it("allows explicitly configured real HTTPS admin API access", async () => {
    await expect(loadNextConfig()).resolves.toMatchObject({ default: { reactStrictMode: true } });
  });

  it.each([undefined, "", " ", "mock", "REAL", "invalid", " real "])(
    "rejects missing or non-real API mode: %s", async (mode) => {
      vi.stubEnv("NEXT_PUBLIC_API_MODE", mode);
      await expect(loadNextConfig()).rejects.toThrow("NEXT_PUBLIC_API_MODE");
    },
  );

  it.each([
    ["missing URL", undefined],
    ["empty URL", " "],
    ["malformed URL", "not-a-url"],
    ["relative URL", "/api/v1/admin"],
    ["HTTP backend", "http://admin-api.example.invalid/api/v1/admin"],
    ["HTTP loopback", "http://localhost:8080/api/v1/admin"],
    ["unsupported protocol", "ftp://admin-api.example.invalid/api/v1/admin"],
    ["username", "https://synthetic-user@admin-api.example.invalid/api/v1/admin"],
    ["password", "https://synthetic-user:synthetic-secret@admin-api.example.invalid/api/v1/admin"],
    ["query", `${apiUrl}?credential=synthetic-secret`],
    ["fragment", `${apiUrl}#synthetic-secret`],
    ["wrong API path", "https://admin-api.example.invalid/api/v1"],
    ["extra path segment", `${apiUrl}/auth/login`],
    ["path prefix collision", `${apiUrl}-other`],
    ["normalized non-admin path", `${apiUrl}/../users`],
  ] as const)("rejects %s without disclosing the supplied value", async (_label, value) => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", value);
    await expect(loadNextConfig()).rejects.toMatchObject({ message: urlError });
  });
});

describe("deployment and development boundaries", () => {
  it.each(["preview", "production"])("allows a real production build for Vercel %s", async (vercelEnv) => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("VERCEL_ENV", vercelEnv);
    await expect(loadNextConfig()).resolves.toMatchObject({ default: { reactStrictMode: true } });
  });

  it("preserves local development Mock mode with a loopback URL", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    await expect(loadNextConfig()).resolves.toMatchObject({ default: { reactStrictMode: true } });
  });

  it("still rejects Mock mode in production without Vercel metadata", async () => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
    await expect(loadNextConfig()).rejects.toThrow("운영 빌드에서는 NEXT_PUBLIC_API_MODE=mock을 사용할 수 없습니다.");
  });

  it.each([
    `  ${apiUrl}///  `,
    "https://admin-api.example.invalid/context/api/v1/admin/",
  ])("accepts URL normalization supported by the runtime relay: %s", async (value) => {
    vi.stubEnv("NODE_ENV", "production");
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", value);
    await expect(loadNextConfig()).resolves.toMatchObject({ default: { reactStrictMode: true } });
  });
});
