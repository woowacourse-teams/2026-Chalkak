export const APP_STORE_URL = "https://apps.apple.com/kr/app/id6809104229";
export const PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.stonefive.chalkak";

export const TERMS_URL = "https://app.notion.com/p/3c66b8e8e3678064b543c26b5c0f932d";
export const PRIVACY_URL = "https://app.notion.com/p/3b56b8e8e36780af8ec8ea0bf92b97a9";

export type Platform = "ios" | "android" | "other";

/** iPadOS 13 이상은 데스크톱 Safari처럼 Macintosh로 보고하므로 터치 지원 여부를 함께 본다. */
export function detectPlatform(userAgent: string, maxTouchPoints = 0): Platform {
  if (/android/i.test(userAgent)) return "android";
  if (/iphone|ipad|ipod/i.test(userAgent)) return "ios";
  if (/macintosh/i.test(userAgent) && maxTouchPoints > 1) return "ios";
  return "other";
}

export function storeUrlFor(platform: Platform): string | null {
  if (platform === "ios") return APP_STORE_URL;
  if (platform === "android") return PLAY_STORE_URL;
  return null;
}

/** Vercel이 빌드에 넣어 주는 프로덕션 주소를 쓰고, 없으면 별도 지정값이나 로컬 주소를 쓴다. */
export function siteUrl(env: Record<string, string | undefined> = process.env): string {
  if (env.NEXT_PUBLIC_SITE_URL) return env.NEXT_PUBLIC_SITE_URL.replace(/\/$/, "");
  if (env.VERCEL_PROJECT_PRODUCTION_URL) return `https://${env.VERCEL_PROJECT_PRODUCTION_URL}`;
  return "http://localhost:3000";
}
