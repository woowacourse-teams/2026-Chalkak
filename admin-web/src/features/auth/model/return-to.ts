const INTERNAL_ORIGIN = "https://admin.invalid";
const ADMIN_PATH = /^\/(?:$|(?:posts|users|topics|audit-logs|pushes)(?:\/|$))/;

/** Only return to an internal admin screen, never a login loop or external URL. */
export function getSafeReturnTo(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//")) {
    return "/";
  }

  try {
    const decoded = decodeURIComponent(value);
    if (/[\\\u0000-\u0020\u007f]/.test(decoded) || decoded.startsWith("//")) {
      return "/";
    }
    const url = new URL(value, INTERNAL_ORIGIN);
    if (url.origin !== INTERNAL_ORIGIN || !ADMIN_PATH.test(url.pathname)) {
      return "/";
    }
    return url.pathname + url.search + url.hash;
  } catch {
    return "/";
  }
}

export function getLoginUrl(returnTo: string) {
  const safePath = getSafeReturnTo(returnTo);
  return safePath === "/" ? "/login" : "/login?" + new URLSearchParams({ returnTo: safePath });
}
