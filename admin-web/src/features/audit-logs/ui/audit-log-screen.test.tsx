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

it("shows schedule audit labels and links to the settings even after deletion", async () => {
  const result = await getApiClient().request<{ scheduleId: string }>("/topic-push-schedules", { method: "POST", body: { sendTime: "12:00:00" } });
  await getApiClient().request(`/topic-push-schedules/${result.scheduleId}`, { method: "DELETE" });
  mount();
  await screen.findAllByRole("link", { name: "주제 알림 설정 보기 →" });
  expect(screen.getAllByText("알림 시간 등록")).toHaveLength(2);
  for (const link of screen.getAllByRole("link", { name: "주제 알림 설정 보기 →" })) expect(link).toHaveAttribute("href", "/pushes");
  fireEvent.change(screen.getByRole("combobox", { name: "대상" }), { target: { value: "TOPIC_PUSH_SCHEDULE" } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(navigation.push).toHaveBeenCalledWith("/audit-logs?page=1&targetType=TOPIC_PUSH_SCHEDULE");
});


it("shows banner actions and returns to the list without inventing a detail route", async () => {
  const result = await getApiClient().request<{ id: string }>("/banners", { method: "POST", body: { title: "새 배너", subtitle: "부제목", webViewUrl: "https://example.com/test", thumbnailUrl: "https://example.com/image.png" } });
  await getApiClient().request(`/banners/${result.id}`, { method: "DELETE" });
  mount();
  const links = await screen.findAllByRole("link", { name: "배너 보기 →" });
  expect(links).toHaveLength(2);
  for (const link of links) expect(link).toHaveAttribute("href", "/banners");
  expect(screen.getAllByText("배너 등록")).toHaveLength(2);
  fireEvent.change(screen.getByRole("combobox", { name: "대상" }), { target: { value: "BANNER" } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(navigation.push).toHaveBeenCalledWith("/audit-logs?page=1&targetType=BANNER");
});

it("shows reminder actions and returns to the reminder list without a detail route", async () => {
  const result = await getApiClient().request<{ reminderId: string }>("/reminders", { method: "POST", body: { title: "새 리마인더", body: "본문", repeat: "ONCE", target: "ALL", scheduledAt: "2099-10-25T12:00:00Z" } });
  await getApiClient().request(`/reminders/${result.reminderId}`, { method: "PUT", body: { title: "고친 리마인더", body: "본문", repeat: "ONCE", target: "ALL", scheduledAt: "2099-10-25T12:00:00Z" } });
  await getApiClient().request(`/reminders/${result.reminderId}`, { method: "DELETE" });
  mount();
  const links = await screen.findAllByRole("link", { name: "리마인더 보기 →" });
  expect(links).toHaveLength(3);
  for (const link of links) expect(link).toHaveAttribute("href", "/reminders");
  expect(screen.getAllByText("리마인더 등록")).toHaveLength(2);
  expect(screen.getAllByText("리마인더 수정")).toHaveLength(2);
  expect(screen.getAllByText("리마인더 삭제")).toHaveLength(2);
  fireEvent.change(screen.getByRole("combobox", { name: "대상" }), { target: { value: "REMINDER" } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(navigation.push).toHaveBeenCalledWith("/audit-logs?page=1&targetType=REMINDER");
});

it("labels AI prompt version creation and links to the version detail", async () => {
  const created = await getApiClient().request<{ promptVersionId: string }>("/ai-prompt-versions", { method: "POST", body: {
    type: "PHOTO_REVIEW", systemPrompt: "사진을 평가하세요.", userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
  } });
  mount();
  const link = await screen.findByRole("link", { name: "AI 프롬프트 버전 보기 →" });
  expect(link).toHaveAttribute("href", `/ai-prompts/${created.promptVersionId}`);
  expect(screen.getByText("AI 프롬프트 버전 생성", { selector: "strong" })).toBeInTheDocument();
  fireEvent.change(screen.getByRole("combobox", { name: "작업" }), { target: { value: "AI_PROMPT_VERSION_CREATED" } });
  fireEvent.change(screen.getByRole("combobox", { name: "대상" }), { target: { value: "AI_PROMPT_VERSION" } });
  fireEvent.click(screen.getByRole("button", { name: "조회" }));
  expect(navigation.push).toHaveBeenCalledWith("/audit-logs?page=1&action=AI_PROMPT_VERSION_CREATED&targetType=AI_PROMPT_VERSION");
});

it("does not link an AI prompt audit entry whose target id is not a UUID", async () => {
  server.use(http.get("*/api/v1/admin/audit-logs", () => HttpResponse.json({
    currentPage: 1, pageSize: 20, hasNext: false, auditLogs: [{
      auditLogId: "a", actorAdminId: "b", actorUsername: "demo-admin", action: "AI_PROMPT_VERSION_CREATED", targetType: "AI_PROMPT_VERSION",
      targetId: "../users", reason: null, beforeState: {}, afterState: {}, occurredAt: "2026-10-20T00:00:00Z", requestId: "r",
    }],
  })));
  mount();
  expect(await screen.findByText("AI 프롬프트 버전 생성", { selector: "strong" })).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: /보기/ })).not.toBeInTheDocument();
});
