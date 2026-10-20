export interface BannerInput {
  title: string;
  subtitle: string;
  webViewUrl: string;
  thumbnailUrl: string;
}
export interface Banner extends BannerInput { id: string; createdAt: string }
export interface BannerListResponse {
  currentPage: number; pageSize: number; hasNext: boolean; banners: Banner[];
}
// Match Java trim().strip(): ASCII controls and Character.isWhitespace spaces.
export function normalizeBannerValue(value: string) {
  return value.replace(/^[\u0000-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0000-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/gu, "");
}
export function normalizeBanner(input: BannerInput): BannerInput {
  return { title: normalizeBannerValue(input.title), subtitle: normalizeBannerValue(input.subtitle), webViewUrl: normalizeBannerValue(input.webViewUrl), thumbnailUrl: normalizeBannerValue(input.thumbnailUrl) };
}
export function isHttpsUrl(value: string) {
  try { const url = new URL(value); return /^https:\/\//i.test(value) && url.protocol === "https:" && Boolean(url.hostname); }
  catch { return false; }
}
export function validateBanner(input: BannerInput): Partial<Record<keyof BannerInput, string>> {
  const normalized = normalizeBanner(input);
  const errors: Partial<Record<keyof BannerInput, string>> = {};
  for (const [key, label, max] of [["title", "제목", 50], ["subtitle", "부제목", 100], ["webViewUrl", "웹뷰 URL", 2000], ["thumbnailUrl", "썸네일 URL", 2000]] as const) {
    const value = normalized[key];
    if (!value || Array.from(value).length > max) errors[key] = `${label}을(를) 1~${max.toLocaleString()}자로 입력해 주세요.`;
    else if ((key === "webViewUrl" || key === "thumbnailUrl") && !isHttpsUrl(value)) errors[key] = `${label}에 HTTPS 절대 주소를 입력해 주세요.`;
  }
  return errors;
}
