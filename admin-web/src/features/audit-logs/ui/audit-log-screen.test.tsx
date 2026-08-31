import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { http, HttpResponse } from "msw";

import { postIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import { getApiClient } from "@/shared/api/client";
import { createQueryClient } from "@/shared/query/query-client";

import { AuditLogScreen } from "./audit-log-screen";

const navigation = vi.hoisted(() => ({ push: vi.fn(), params: new URLSearchParams() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: navigation.push }), useSearchParams: () => navigation.params }));

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  navigation.push.mockReset();
  navigation.params = new URLSearchParams();
});
afterEach(() => vi.unstubAllEnvs());

function mount() {
  render(<QueryClientProvider client={createQueryClient()}><AuditLogScreen /></QueryClientProvider>);
}

describe("administrator action history", () => {
  it("shows an honest empty state before any operation", async () => {
    mount();
    expect(await screen.findByRole("heading", { name: "처리 이력이 없습니다" })).toBeInTheDocument();
  });

  it("shows a completed review and links back to its target", async () => {
    await getApiClient().request(`/posts/${postIds.pending}/moderation`, { method: "PUT", body: { status: "APPROVED" } });
    mount();
    expect(await screen.findByText("처리자 demo-admin")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "게시물 보기 →" })).toHaveAttribute("href", `/posts/${postIds.pending}?returnTo=%2Faudit-logs`);
    fireEvent.click(screen.getByText("변경 내용"));
    expect(screen.getByText(/"APPROVED"/)).toBeInTheDocument();
  });

  it("filters using readable action labels without typing identifiers", async () => {
    mount();
    fireEvent.change(screen.getByRole("combobox", { name: "작업" }), { target: { value: "USER_BANNED" } });
    fireEvent.change(screen.getByRole("combobox", { name: "대상" }), { target: { value: "USER" } });
    fireEvent.click(screen.getByRole("button", { name: "조회" }));
    expect(navigation.push).toHaveBeenCalledWith("/audit-logs?page=1&action=USER_BANNED&targetType=USER");
    await screen.findByRole("heading", { name: "처리 이력이 없습니다" });
  });

  it("shows permissions failure without attempting a write", async () => {
    server.use(http.get("*/api/v1/admin/audit-logs", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "관리자 권한이 없습니다." }, { status: 403 })));
    mount();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("관리자 권한이 없습니다."));
  });
});
