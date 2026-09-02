import { QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import { server } from "@/mocks/server";
import { getApiClient } from "@/shared/api/client";
import { ADMIN_SESSION_EXPIRED_EVENT } from "@/shared/api/session-events";
import { createQueryClient } from "@/shared/query/query-client";

import { AdminSessionProvider, useAdminSession } from "./admin-session-provider";

type SessionObserver = (session: ReturnType<typeof useAdminSession>) => void;

function SessionProbe({ onSession }: { onSession?: SessionObserver }) {
  const session = useAdminSession();
  onSession?.(session);
  return <>
    <p data-testid="session">{session.status}</p>
    <p>{session.admin?.username}</p>
    <button onClick={() => { void session.login("demo-admin", "demo-only"); }}>로그인</button>
    <button onClick={() => { void session.logout().catch(() => undefined); }}>로그아웃</button>
  </>;
}

function mount(onSession?: SessionObserver) {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  const queryClient = createQueryClient();
  render(<QueryClientProvider client={queryClient}><AdminSessionProvider><SessionProbe onSession={onSession} /></AdminSessionProvider></QueryClientProvider>);
  return queryClient;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => { resolve = complete; });
  return { promise, resolve };
}

afterEach(() => vi.unstubAllEnvs());

describe("admin session", () => {
  it("restores a verified session on reload without a client-side token", async () => {
    server.use(http.get("*/api/v1/admin/auth/me", () => HttpResponse.json({ adminId: "admin-test", username: "restored-admin" })));
    mount();
    expect(await screen.findByText("restored-admin")).toBeInTheDocument();
    expect(screen.getByTestId("session")).toHaveTextContent("authenticated");
  });

  it("logs in and removes administrator query data on logout", async () => {
    const queryClient = mount();
    await waitFor(() => expect(screen.getByTestId("session")).toHaveTextContent("anonymous"));
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    expect(await screen.findByText("demo-admin")).toBeInTheDocument();
    queryClient.setQueryData(["admin", "posts"], { private: true });
    fireEvent.click(screen.getByRole("button", { name: "로그아웃" }));
    await waitFor(() => expect(screen.getByTestId("session")).toHaveTextContent("anonymous"));
    expect(queryClient.getQueryData(["admin", "posts"])).toBeUndefined();
  });

  it("clears session and caches once a protected request returns 401", async () => {
    const queryClient = mount();
    await waitFor(() => expect(screen.getByTestId("session")).toHaveTextContent("anonymous"));
    fireEvent.click(screen.getByRole("button", { name: "로그인" }));
    await screen.findByText("demo-admin");
    queryClient.setQueryData(["admin", "users"], ["private"]);
    server.use(http.get("*/api/v1/admin/posts", () => HttpResponse.json({ errorCode: "UNAUTHORIZED", message: "로그인이 필요합니다." }, { status: 401 })));
    await act(async () => { await getApiClient().request("/posts").catch(() => undefined); });
    expect(screen.getByTestId("session")).toHaveTextContent("anonymous");
    expect(queryClient.getQueryData(["admin", "users"])).toBeUndefined();
  });

  it("does not retry or masquerade as logged out on a permissions error", async () => {
    const requests = vi.fn(() => HttpResponse.json({ errorCode: "FORBIDDEN", message: "관리자 권한이 없습니다." }, { status: 403 }));
    server.use(http.get("*/api/v1/admin/auth/me", requests));
    mount();
    await waitFor(() => expect(screen.getByTestId("session")).toHaveTextContent("forbidden"));
    expect(requests).toHaveBeenCalledTimes(1);
  });

  it("does not reuse cached administrator data after an expiry event", async () => {
    const queryClient = mount();
    await waitFor(() => expect(screen.getByTestId("session")).toHaveTextContent("anonymous"));
    queryClient.setQueryData(["admin", "topics"], ["private"]);
    act(() => window.dispatchEvent(new Event(ADMIN_SESSION_EXPIRED_EVENT)));
    expect(queryClient.getQueryData(["admin", "topics"])).toBeUndefined();
  });

  it("keeps logout pending and prevents a new login until the delayed logout finishes", async () => {
    const delayedLogout = deferred<Response>();
    const loginRequests = vi.fn(() => HttpResponse.json({
      adminId: "dddddddd-dddd-4ddd-8ddd-dddddddddddd", username: "demo-admin", expiresIn: 3600,
    }));
    const logoutRequests = vi.fn(() => delayedLogout.promise);
    server.use(
      http.post("*/api/v1/admin/auth/login", loginRequests),
      http.post("*/api/v1/admin/auth/logout", logoutRequests),
    );
    let session!: ReturnType<typeof useAdminSession>;
    const queryClient = mount((value) => { session = value; });
    await waitFor(() => expect(session.status).toBe("anonymous"));
    await act(async () => { await session.login("demo-admin", "demo-only"); });
    queryClient.setQueryData(["admin", "posts"], { private: true });

    let pendingLogout!: Promise<void>;
    await act(async () => { pendingLogout = session.logout(); });
    await waitFor(() => expect(logoutRequests).toHaveBeenCalledOnce());
    expect(session.status).toBe("logging-out");
    expect(session.admin).toBeNull();
    expect(queryClient.getQueryData(["admin", "posts"])).toBeUndefined();
    await expect(session.login("demo-admin", "demo-only")).rejects.toMatchObject({
      name: "ApiError", message: "로그인 상태를 변경하고 있습니다. 잠시 기다려 주세요.",
    });
    expect(loginRequests).toHaveBeenCalledOnce();
    expect(session.status).toBe("logging-out");

    await act(async () => {
      delayedLogout.resolve(new HttpResponse(null, { status: 204 }));
      await pendingLogout;
    });
    expect(session.status).toBe("anonymous");
    await act(async () => { await session.login("demo-admin", "demo-only"); });
    expect(session.status).toBe("authenticated");
    expect(loginRequests).toHaveBeenCalledTimes(2);
  });

  it("purges prior administrator cache when a focus refresh verifies a different identity", async () => {
    let currentAdmin = { adminId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", username: "first-admin" };
    const meRequests = vi.fn(() => HttpResponse.json(currentAdmin));
    server.use(http.get("*/api/v1/admin/auth/me", meRequests));
    const queryClient = mount();
    await screen.findByText("first-admin");
    queryClient.setQueryData(["admin", "posts"], { previousAdmin: true });
    queryClient.setQueryData(["admin", "topics"], ["previous-admin-data"]);
    queryClient.setQueryData(["public", "settings"], { retained: true });

    currentAdmin = { adminId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", username: "second-admin" };
    act(() => { window.dispatchEvent(new Event("focus")); });

    await screen.findByText("second-admin");
    expect(screen.getByTestId("session")).toHaveTextContent("authenticated");
    expect(queryClient.getQueryData(["admin", "posts"])).toBeUndefined();
    expect(queryClient.getQueryData(["admin", "topics"])).toBeUndefined();
    expect(queryClient.getQueryData(["public", "settings"])).toEqual({ retained: true });
    expect(meRequests).toHaveBeenCalledTimes(2);
  });

  it("retains administrator cache when refresh verifies the same identity", async () => {
    const meRequests = vi.fn(() => HttpResponse.json({ adminId: "admin-test", username: "same-admin" }));
    server.use(http.get("*/api/v1/admin/auth/me", meRequests));
    let session!: ReturnType<typeof useAdminSession>;
    const queryClient = mount((value) => { session = value; });
    await screen.findByText("same-admin");
    queryClient.setQueryData(["admin", "posts"], { currentAdmin: true });

    await act(async () => { await session.refresh(); });

    expect(meRequests).toHaveBeenCalledTimes(2);
    expect(queryClient.getQueryData(["admin", "posts"])).toEqual({ currentAdmin: true });
    expect(screen.getByTestId("session")).toHaveTextContent("authenticated");
  });
});
