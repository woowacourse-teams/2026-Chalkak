import { getApiClient } from "@/shared/api/client";

export interface CurrentAdmin {
  adminId: string;
  username: string;
}

export interface AdminLoginSession extends CurrentAdmin {
  expiresIn: number;
}

export function fetchCurrentAdmin(signal?: AbortSignal) {
  return getApiClient().request<CurrentAdmin>("/auth/me", { signal });
}

export function loginAdmin(username: string, password: string) {
  return getApiClient().request<AdminLoginSession>("/auth/login", {
    method: "POST",
    body: { username, password },
  });
}

export function logoutAdmin() {
  return getApiClient().request<void>("/auth/logout", { method: "POST" });
}
