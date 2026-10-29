import type { AdminAppVersion, AdminAppVersionInput, AppPlatform } from "@/shared/api/contracts";

export const platformLabels: Record<AppPlatform, string> = { ANDROID: "Android", IOS: "iOS" };
export const platforms: AppPlatform[] = ["ANDROID", "IOS"];

// Match Java String.strip(), including its treatment of non-breaking spaces.
export function stripInput(value: string) {
  return value.replace(/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g, "");
}

export function isSupportedVersion(value: string) {
  return /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.test(value)
    && value.split(".").every((part) => Number(part) <= 2_147_483_647);
}

export function isStoreUrl(platform: AppPlatform, input: string) {
  const value = stripInput(input);
  if (!value || [...value].length > 2_000 || /[\s<>"{}|^`\\\[\]]/.test(value) || /%(?![0-9a-f]{2})/i.test(value)) return false;
  // Inspect the raw authority/path: URL() would silently remove dot segments and ports.
  const parts = /^https:\/\/(play\.google\.com|apps\.apple\.com)(?::443)?(\/[^?#]*)(?:\?([^#]*))?$/i.exec(value);
  if (!parts) return false;
  const [, host, path, query] = parts;
  try {
    const decodedPath = decodeURIComponent(path);
    if (/%2f/i.test(path) || decodedPath.includes("\\") || decodedPath.split("/").some((part) => part === "." || part === "..")) return false;
    const params = new Map<string, string>();
    if (query !== undefined) {
      for (const item of query.split("&")) {
        const equal = item.indexOf("=");
        if (equal < 0) return false;
        const decode = (part: string) => decodeURIComponent(part.replace(/\+/g, " "));
        const key = decode(item.slice(0, equal));
        if (params.has(key)) return false;
        params.set(key, decode(item.slice(equal + 1)));
      }
    }
    const language = (key: string) => !params.has(key) || /^[A-Za-z_-]{2,10}$/.test(params.get(key)!);
    if (platform === "ANDROID") {
      return host.toLowerCase() === "play.google.com" && path === "/store/apps/details"
        && /^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(params.get("id") ?? "")
        && [...params.keys()].every((key) => ["id", "hl", "gl"].includes(key)) && language("hl") && language("gl");
    }
    return host.toLowerCase() === "apps.apple.com" && /^\/(?:[a-zA-Z]{2}\/)?app\/(?:[^/]+\/)?id[1-9][0-9]*$/.test(path)
      && [...params.keys()].every((key) => ["l", "mt"].includes(key)) && language("l") && (!params.has("mt") || params.get("mt") === "8");
  } catch { return false; }
}

export function normalizeAppVersion(input: AdminAppVersionInput): AdminAppVersionInput {
  return { ...input, storeUrl: stripInput(input.storeUrl), reason: stripInput(input.reason) };
}

export function validateAppVersion(platform: AppPlatform, input: AdminAppVersionInput) {
  if (!isSupportedVersion(input.minimumSupportedVersion)) return "최소 지원 버전은 1.10.0처럼 세 숫자로 입력해 주세요. 각 숫자는 0~2,147,483,647이며 앞자리 0은 사용할 수 없습니다.";
  if (!isStoreUrl(platform, input.storeUrl)) return `${platformLabels[platform]}의 공식 HTTPS 스토어 앱 상세 주소를 입력해 주세요. 추적 쿼리·단축 주소는 사용할 수 없습니다.`;
  const count = [...stripInput(input.reason)].length;
  if (count < 1 || count > 500) return "변경 사유는 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.";
  return null;
}

export function sameAppVersion(current: AdminAppVersion, input: Pick<AdminAppVersionInput, "minimumSupportedVersion" | "storeUrl">) {
  return current.minimumSupportedVersion === input.minimumSupportedVersion && current.storeUrl === stripInput(input.storeUrl);
}

export function isVersionIncrease(before: string | null, after: string) {
  if (!isSupportedVersion(after)) return false;
  const previous = (before ?? "0.0.0").split(".").map(Number);
  const next = after.split(".").map(Number);
  for (let index = 0; index < 3; index++) {
    if (previous[index] !== next[index]) return next[index] > previous[index];
  }
  return false;
}
