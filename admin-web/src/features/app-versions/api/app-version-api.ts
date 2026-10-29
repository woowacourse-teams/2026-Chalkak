import { getApiClient } from "@/shared/api/client";
import type { AdminAppVersion, AdminAppVersionInput, AdminAppVersionListResponse, AppPlatform } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { isSupportedVersion, isStoreUrl, platforms } from "../model/app-version-form";

function invalidResponse(): never {
  throw new ApiError({ kind: "invalid-response", message: "앱 버전 설정 응답이 올바르지 않습니다. 다시 조회해 주세요." });
}

function setting(value: unknown, platform: AppPlatform): AdminAppVersion {
  if (!value || typeof value !== "object") return invalidResponse();
  const row = value as AdminAppVersion;
  if (row.platform !== platform) return invalidResponse();
  if (row.minimumSupportedVersion === null) {
    if ([row.storeUrl, row.revision, row.lastChangedBy, row.changeReason, row.createdAt, row.updatedAt].some((field) => field !== null)) return invalidResponse();
    return row;
  }
  if (typeof row.minimumSupportedVersion !== "string" || !isSupportedVersion(row.minimumSupportedVersion)
    || typeof row.storeUrl !== "string" || !isStoreUrl(platform, row.storeUrl)
    || !Number.isSafeInteger(row.revision) || row.revision === null || row.revision < 0
    || ![row.lastChangedBy, row.changeReason].every((field) => field === null || typeof field === "string")
    || ![row.createdAt, row.updatedAt].every((field) => typeof field === "string" && Number.isFinite(Date.parse(field)))) return invalidResponse();
  return row;
}

export async function fetchAppVersions(signal?: AbortSignal): Promise<AdminAppVersionListResponse> {
  const value = await getApiClient().request<AdminAppVersionListResponse>("/app-versions", { signal });
  if (!value || !Array.isArray(value.appVersions) || value.appVersions.length !== 2) return invalidResponse();
  return { appVersions: platforms.map((platform) => setting(value.appVersions.find((row) => row?.platform === platform), platform)) };
}

export async function saveAppVersion(platform: AppPlatform, body: AdminAppVersionInput) {
  const value = await getApiClient().request<AdminAppVersion>(`/app-versions/${platform}`, { method: "PUT", body });
  const saved = setting(value, platform);
  if (saved.revision === null) return invalidResponse();
  return saved;
}
