import { http, HttpResponse } from "msw";
import type { AdminAppVersion, AdminAppVersionInput, AppPlatform } from "@/shared/api/contracts";
import { normalizeAppVersion, sameAppVersion, validateAppVersion } from "@/features/app-versions/model/app-version-form";

// Demonstration values only; these are never sent to a real backend.
export const appVersionFixtures: AdminAppVersion[] = [
  { platform: "ANDROID", minimumSupportedVersion: "1.1.0", storeUrl: "https://play.google.com/store/apps/details?id=com.example.chalkak", revision: 0, lastChangedBy: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", changeReason: "데모 설정", createdAt: "2026-10-29T01:00:00Z", updatedAt: "2026-10-29T01:00:00Z" },
  { platform: "IOS", minimumSupportedVersion: null, storeUrl: null, revision: null, lastChangedBy: null, changeReason: null, createdAt: null, updatedAt: null },
];
let settings = structuredClone(appVersionFixtures);
export function resetAppVersionMocks() { settings = structuredClone(appVersionFixtures); }
const path = "*/api/v1/admin/app-versions";
const snapshot = (row: AdminAppVersion) => ({ platform: row.platform, minimumSupportedVersion: row.minimumSupportedVersion, storeUrl: row.storeUrl, revision: row.revision });

export function createAppVersionHandlers(record: (action: string, id: string, reason: string, before: Record<string, unknown>, after: Record<string, unknown>) => void) {
  return [
    http.get(path, () => HttpResponse.json({ appVersions: settings })),
    http.put(path + "/:platform", async ({ request, params }) => {
      const platform = params.platform as AppPlatform;
      const current = settings.find((row) => row.platform === platform);
      if (!current) return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "지원하지 않는 플랫폼입니다." }, { status: 400 });
      const body = await request.json() as AdminAppVersionInput;
      const validation = validateAppVersion(platform, body);
      if (validation) return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: validation }, { status: 400 });
      const input = normalizeAppVersion(body);
      if (sameAppVersion(current, input)) return HttpResponse.json(current);
      if (input.revision !== current.revision) return HttpResponse.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "다른 관리자가 설정을 변경했습니다." }, { status: 400 });
      const now = new Date().toISOString();
      const saved: AdminAppVersion = { ...current, minimumSupportedVersion: input.minimumSupportedVersion, storeUrl: input.storeUrl, revision: current.revision === null ? 0 : current.revision + 1, lastChangedBy: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", changeReason: input.reason, createdAt: current.createdAt ?? now, updatedAt: now };
      settings = settings.map((row) => row.platform === platform ? saved : row);
      record(current.revision === null ? "APP_VERSION_CREATED" : "APP_VERSION_UPDATED", platform === "ANDROID" ? "eeeeeeee-eeee-4eee-8eee-eeeeeeeeee01" : "eeeeeeee-eeee-4eee-8eee-eeeeeeeeee02", input.reason, current.revision === null ? {} : snapshot(current), snapshot(saved));
      return HttpResponse.json(saved);
    }),
  ];
}
