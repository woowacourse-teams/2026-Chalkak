"use client";

import { useQueryClient } from "@tanstack/react-query";
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
  type PropsWithChildren,
} from "react";

import { resolveApiMode } from "@/shared/api/config";
import { cancelAdminRequests } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import { ADMIN_SESSION_EXPIRED_EVENT } from "@/shared/api/session-events";

import { fetchCurrentAdmin, loginAdmin, logoutAdmin, type CurrentAdmin } from "./api/auth-api";

type SessionStatus = "loading" | "logging-out" | "authenticated" | "anonymous" | "error" | "forbidden";

interface SessionState {
  admin: CurrentAdmin | null;
  status: SessionStatus;
  error: string | null;
}

interface AdminSession extends SessionState {
  isMock: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AdminSessionContext = createContext<AdminSession | null>(null);
const anonymousSession: SessionState = { admin: null, status: "anonymous", error: null };

export function AdminSessionProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<SessionState>({ admin: null, status: "loading", error: null });
  const revision = useRef(0);
  const currentRequest = useRef<AbortController | null>(null);
  const expiryTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const operation = useRef<"login" | "logout" | null>(null);
  const verifiedAdminId = useRef<string | null>(null);
  const isMock = resolveApiMode() === "mock";

  const discardSession = useCallback(() => {
    revision.current += 1;
    currentRequest.current?.abort();
    cancelAdminRequests();
    verifiedAdminId.current = null;
    if (expiryTimer.current) clearTimeout(expiryTimer.current);
    // Cancel first so an in-flight response cannot repopulate the next session's cache.
    void queryClient.cancelQueries({ queryKey: ["admin"] });
    queryClient.removeQueries({ queryKey: ["admin"] });
    setSession(anonymousSession);
  }, [queryClient]);

  const refresh = useCallback((): Promise<void> => {
    if (operation.current) return Promise.resolve();
    currentRequest.current?.abort();
    const controller = new AbortController();
    currentRequest.current = controller;
    const requestRevision = ++revision.current;
    // Normalize synchronous configuration failures into the async request lifecycle.
    return Promise.resolve().then(async () => {
      if (requestRevision !== revision.current || controller.signal.aborted) return;
      try {
        const admin = await fetchCurrentAdmin(controller.signal);
        if (requestRevision !== revision.current || controller.signal.aborted) return;
        if (verifiedAdminId.current && verifiedAdminId.current !== admin.adminId) {
          cancelAdminRequests();
          await queryClient.cancelQueries({ queryKey: ["admin"] });
          queryClient.removeQueries({ queryKey: ["admin"] });
        }
        if (requestRevision !== revision.current || controller.signal.aborted) return;
        verifiedAdminId.current = admin.adminId;
        setSession({ admin, status: "authenticated", error: null });
      } catch (error) {
        if (requestRevision !== revision.current || controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 401) {
          discardSession();
          return;
        }
        void queryClient.cancelQueries({ queryKey: ["admin"] });
        queryClient.removeQueries({ queryKey: ["admin"] });
        setSession({
          admin: null,
          status: error instanceof ApiError && error.status === 403 ? "forbidden" : "error",
          error: error instanceof ApiError ? error.message : "로그인 상태를 확인하지 못했습니다.",
        });
      }
    });
  }, [discardSession, queryClient]);

  useEffect(() => {
    void refresh();
    window.addEventListener(ADMIN_SESSION_EXPIRED_EVENT, discardSession);
    return () => {
      revision.current += 1;
      currentRequest.current?.abort();
      if (expiryTimer.current) clearTimeout(expiryTimer.current);
      window.removeEventListener(ADMIN_SESSION_EXPIRED_EVENT, discardSession);
    };
  }, [discardSession, refresh]);

  useEffect(() => {
    if (session.status !== "authenticated") return;
    const recheck = () => { void refresh(); };
    const interval = window.setInterval(recheck, 60_000);
    window.addEventListener("focus", recheck);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener("focus", recheck);
    };
  }, [session.status, refresh]);

  const login = useCallback(async (username: string, password: string) => {
    if (operation.current) throw new ApiError({ kind: "api", message: "로그인 상태를 변경하고 있습니다. 잠시 기다려 주세요." });
    operation.current = "login";
    discardSession();
    setSession({ admin: null, status: "loading", error: null });
    const requestRevision = revision.current;
    try {
      const result = await loginAdmin(username, password);
      if (requestRevision !== revision.current) return;
      await queryClient.cancelQueries({ queryKey: ["admin"] });
      if (requestRevision !== revision.current) return;
      queryClient.removeQueries({ queryKey: ["admin"] });
      verifiedAdminId.current = result.adminId;
      // The access token never enters React state or browser storage.
      setSession({ admin: { adminId: result.adminId, username: result.username }, status: "authenticated", error: null });
      if (Number.isFinite(result.expiresIn) && result.expiresIn > 0) {
        expiryTimer.current = setTimeout(discardSession, Math.min(result.expiresIn * 1000, 2_147_483_647));
      }
    } catch (error) {
      if (requestRevision === revision.current) discardSession();
      throw error;
    } finally {
      operation.current = null;
    }
  }, [discardSession, queryClient]);

  const logout = useCallback(async () => {
    if (operation.current) throw new ApiError({ kind: "api", message: "로그인 상태를 변경하고 있습니다. 잠시 기다려 주세요." });
    operation.current = "logout";
    discardSession();
    setSession({ admin: null, status: "logging-out", error: null });
    let failed = false;
    try {
      await logoutAdmin();
    } catch (error) {
      failed = true;
      throw error;
    } finally {
      discardSession();
      if (failed) setSession({ ...anonymousSession, error: "로그아웃 확인에 실패했습니다." });
      operation.current = null;
    }
  }, [discardSession]);

  return (
    <AdminSessionContext.Provider value={{ ...session, isMock, login, logout, refresh }}>
      {children}
    </AdminSessionContext.Provider>
  );
}

export function useAdminSession() {
  const session = useContext(AdminSessionContext);
  if (!session) throw new Error("AdminSessionProvider가 필요합니다.");
  return session;
}
