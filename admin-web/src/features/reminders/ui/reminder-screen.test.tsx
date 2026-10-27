import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { reminderFixtures } from "@/mocks/reminder-handlers";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { getCurrentNavigation } from "@/shared/navigation/admin-navigation";
import { ToastProvider } from "@/shared/ui/toast";
import { ReminderScreen } from "./reminder-screen";

const navigation = vi.hoisted(() => ({ push: vi.fn(), search: "" }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation, useSearchParams: () => new URLSearchParams(navigation.search) }));
beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  navigation.push.mockReset(); navigation.search = "";
});
afterEach(() => vi.unstubAllEnvs());
function mount() {
  const client = createQueryClient();
  client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}><ToastProvider><ReminderScreen /></ToastProvider></QueryClientProvider>);
  return client;
}
function card(title: string) {
  return screen.getByText(title).closest("li") as HTMLElement;
}
function fillReminder() {
  fireEvent.change(screen.getByRole("textbox", { name: "제목" }), { target: { value: "마감 30분 전이에요" } });
  fireEvent.change(screen.getByRole("textbox", { name: "본문" }), { target: { value: "아직 늦지 않았어요." } });
}

describe("reminder screen", () => {
  it("shows schedule, target, and status, and hides editing for a passed one-time reminder", async () => {
    mount();
    expect(screen.getByLabelText("콘텐츠 불러오는 중")).toBeInTheDocument();
    const [daily, upcoming, passed] = reminderFixtures;
    await screen.findByText(daily.title);
    expect(within(card(daily.title)).getByText("매일 21:00 KST · 2026. 10. 20.부터")).toBeInTheDocument();
    expect(within(card(daily.title)).getByText("오늘 주제 미참여자")).toBeInTheDocument();
    expect(within(card(daily.title)).getByText("매일 발송 중")).toBeInTheDocument();
    expect(within(card(upcoming.title)).getByText("발송 예정")).toBeInTheDocument();
    expect(within(card(upcoming.title)).getByRole("button", { name: `${upcoming.title} 수정` })).toBeInTheDocument();
    expect(within(card(passed.title)).getByText("발송 시각 지남")).toBeInTheDocument();
    expect(within(card(passed.title)).queryByRole("button", { name: `${passed.title} 수정` })).not.toBeInTheDocument();
    expect(within(card(passed.title)).getByRole("button", { name: `${passed.title} 삭제` })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "리마인더 처리 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=REMINDER");
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
  });
  it("uses server pagination without inventing a total count", async () => {
    navigation.search = "page=2";
    server.use(http.get("*/api/v1/admin/reminders", ({ request }) => {
      expect(new URL(request.url).searchParams.get("page")).toBe("2");
      return HttpResponse.json({ currentPage: 2, pageSize: 20, hasNext: true, reminders: reminderFixtures });
    }));
    mount();
    await screen.findByText("2 페이지");
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    expect(navigation.push).toHaveBeenCalledWith("/reminders?page=3");
  });
  it("shows an error and can retry into the empty state", async () => {
    server.use(http.get("*/api/v1/admin/reminders", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "권한이 없습니다." }, { status: 403 })));
    mount();
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    server.use(http.get("*/api/v1/admin/reminders", () => HttpResponse.json({ currentPage: 1, pageSize: 20, hasNext: false, reminders: [] })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await screen.findByText("등록된 리마인더가 없습니다");
  });
  it("confirms the target and KST schedule before creating a daily reminder", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/reminders", async ({ request }) => {
      const body = await request.json(); posted(body);
      return HttpResponse.json({ ...reminderFixtures[0], ...body as object, reminderId: crypto.randomUUID() }, { status: 201 });
    }));
    const client = mount();
    client.setQueryData(queryKeys.auditLogs, {});
    await screen.findByText(reminderFixtures[0].title);
    fireEvent.click(screen.getByRole("button", { name: "리마인더 추가" }));
    fillReminder();
    fireEvent.click(screen.getByRole("radio", { name: "매일 반복" }));
    fireEvent.click(screen.getByRole("radio", { name: "오늘 주제 미참여자" }));
    fireEvent.change(screen.getByLabelText("시작 날짜와 매일 보낼 시각 (KST)"), { target: { value: "2099-10-25T21:00" } });
    fireEvent.click(screen.getByRole("button", { name: "저장 내용 확인" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("오늘 주제 미참여자");
    expect(dialog).toHaveTextContent("매일 21:00 KST · 2099. 10. 25.부터");
    expect(posted).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole("button", { name: "리마인더 등록" }));
    await waitFor(() => expect(posted).toHaveBeenCalledWith({ title: "마감 30분 전이에요", body: "아직 늦지 않았어요.", repeat: "DAILY", target: "TOPIC_NON_PARTICIPANTS", scheduledAt: "2099-10-25T12:00:00.000Z" }));
    await waitFor(() => expect(screen.queryByRole("form", { name: "리마인더 추가" })).not.toBeInTheDocument());
    expect(client.getQueryState(queryKeys.auditLogs)?.isInvalidated).toBe(true);
  });
  it("rejects a past time before sending a request", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/reminders", () => { posted(); return HttpResponse.json({}, { status: 201 }); }));
    mount();
    await screen.findByText(reminderFixtures[0].title);
    fireEvent.click(screen.getByRole("button", { name: "리마인더 추가" }));
    fillReminder();
    fireEvent.change(screen.getByLabelText("발송 시각 (KST)"), { target: { value: "2026-01-01T09:00" } });
    fireEvent.click(screen.getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("alert")).toHaveTextContent("과거일 수 없습니다");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(posted).not.toHaveBeenCalled();
  });
  it("keeps the original start time when only the text of a started daily reminder changes", async () => {
    const put = vi.fn();
    const daily = reminderFixtures[0];
    server.use(http.put("*/api/v1/admin/reminders/:id", async ({ request }) => {
      const body = await request.json(); put(body);
      return HttpResponse.json({ ...daily, ...body as object });
    }));
    mount();
    fireEvent.click(await screen.findByRole("button", { name: `${daily.title} 수정` }));
    fireEvent.change(screen.getByRole("textbox", { name: "제목" }), { target: { value: "고친 제목" } });
    fireEvent.click(screen.getByRole("button", { name: "저장 내용 확인" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "수정 저장" }));
    await waitFor(() => expect(put).toHaveBeenCalledWith({ title: "고친 제목", body: daily.body, repeat: "DAILY", target: daily.target, scheduledAt: daily.scheduledAt }));
  });
  it("shows the server reason when an edit is rejected and keeps the inputs", async () => {
    const upcoming = reminderFixtures[1];
    server.use(http.put("*/api/v1/admin/reminders/:id", () => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다." }, { status: 400 })));
    mount();
    fireEvent.click(await screen.findByRole("button", { name: `${upcoming.title} 수정` }));
    fireEvent.click(screen.getByRole("button", { name: "저장 내용 확인" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "수정 저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다.");
    expect(screen.getByRole("textbox", { name: "본문" })).toHaveValue(upcoming.body);
  });
  it("prevents blind duplicate creation when a response is lost", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/reminders", () => { posted(); return HttpResponse.error(); }));
    mount();
    await screen.findByText(reminderFixtures[0].title);
    fireEvent.click(screen.getByRole("button", { name: "리마인더 추가" }));
    fillReminder();
    fireEvent.change(screen.getByLabelText("발송 시각 (KST)"), { target: { value: "2099-10-25T21:00" } });
    fireEvent.click(screen.getByRole("button", { name: "저장 내용 확인" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "리마인더 등록" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("등록 여부를 먼저 확인");
    expect(screen.queryByRole("button", { name: "저장 내용 확인" })).not.toBeInTheDocument();
    expect(posted).toHaveBeenCalledTimes(1);
  });
  it("deletes without a reason and removes the reminder from the list", async () => {
    const passed = reminderFixtures[2];
    mount();
    fireEvent.click(await screen.findByRole("button", { name: `${passed.title} 삭제` }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("이미 기기에 표시된 푸시는 회수할 수 없으며");
    expect(within(dialog).queryByRole("textbox")).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole("button", { name: "리마인더 삭제" }));
    await waitFor(() => expect(screen.queryByText(passed.title)).not.toBeInTheDocument());
  });
  it("keeps the notification menu selected on the reminder page", () => {
    expect(getCurrentNavigation("/reminders")).toEqual({ href: "/pushes", label: "알림", icon: "clock" });
  });
});
