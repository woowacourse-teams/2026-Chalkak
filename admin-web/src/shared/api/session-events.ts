export const ADMIN_SESSION_EXPIRED_EVENT = "chalkak:admin-session-expired";

export function notifyAdminSessionExpired() {
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(ADMIN_SESSION_EXPIRED_EVENT));
  }
}
