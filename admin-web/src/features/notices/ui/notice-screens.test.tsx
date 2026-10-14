import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { noticeFixtures } from "@/mocks/notice-handlers";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { NoticeListScreen } from "./notice-list-screen";
import { NoticeDetailScreen } from "./notice-detail-screen";
import { NoticeEditScreen, NoticeFormScreen } from "./notice-form-screen";
import type { ReactNode } from "react";

const navigation = vi.hoisted(() => ({ push: vi.fn(), search: "" }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation, useSearchParams: () => new URLSearchParams(navigation.search) }));
beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  navigation.push.mockReset(); navigation.search = "";
});
afterEach(() => vi.unstubAllEnvs());
function mount(node: ReactNode) {
  const client = createQueryClient();
  client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}><ToastProvider>{node}</ToastProvider></QueryClientProvider>);
  return client;
}
function fillNotice() {
  fireEvent.change(screen.getByRole("textbox", { name: "제목" }), { target: { value: "테스트 공지" } });
  fireEvent.change(screen.getByRole("textbox", { name: "본문" }), { target: { value: "공지 본문입니다." } });
}

describe("notice screens", () => {
  it("shows inbox status, KST time, and links to history and creation", async () => {
    mount(<NoticeListScreen />);
    expect(screen.getByLabelText("콘텐츠 불러오는 중")).toBeInTheDocument();
    expect(await screen.findByText("예약 대기")).toBeInTheDocument();
    expect(screen.getByText("알림함 배포 완료")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "공지 작성" })).toHaveAttribute("href", "/notices/new");
    expect(screen.getByRole("link", { name: "공지 처리 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=NOTICE");
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
  });
  it("uses server pagination without inventing a total count", async () => {
    navigation.search = "page=2";
    server.use(http.get("*/api/v1/admin/notices", ({ request }) => {
      expect(new URL(request.url).searchParams.get("page")).toBe("2");
      return HttpResponse.json({ currentPage: 2, pageSize: 20, hasNext: true, notices: noticeFixtures });
    }));
    mount(<NoticeListScreen />);
    await screen.findByText("2 페이지");
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    expect(navigation.push).toHaveBeenCalledWith("/notices?page=3");
  });
  it("shows an error and can retry into the empty state", async () => {
    server.use(http.get("*/api/v1/admin/notices", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "권한이 없습니다." }, { status: 403 })));
    mount(<NoticeListScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    server.use(http.get("*/api/v1/admin/notices", () => HttpResponse.json({ currentPage: 1, pageSize: 20, hasNext: false, notices: [] })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await screen.findByText("등록된 공지가 없습니다");
  });
  it("confirms full content and target before posting a KST reservation", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/notices", async ({ request }) => {
      const body = await request.json(); posted(body);
      return HttpResponse.json({ ...noticeFixtures[0], ...body as object }, { status: 201 });
    }));
    const client = mount(<NoticeFormScreen />);
    client.setQueryData(queryKeys.auditLogs, {});
    fillNotice();
    fireEvent.click(screen.getByRole("radio", { name: "예약 발송" }));
    fireEvent.change(screen.getByLabelText("예약 시각 (KST)"), { target: { value: "2099-10-20T02:00" } });
    fireEvent.click(screen.getByRole("button", { name: "발송 내용 확인" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("전체 회원 대상");
    expect(dialog).toHaveTextContent("공지 본문입니다.");
    expect(posted).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole("button", { name: "공지 예약 등록" }));
    await waitFor(() => expect(posted).toHaveBeenCalledWith({ title: "테스트 공지", body: "공지 본문입니다.", scheduledAt: "2099-10-19T17:00:00.000Z" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/notices/${noticeFixtures[0].noticeId}`));
    expect(client.getQueryState(queryKeys.auditLogs)?.isInvalidated).toBe(true);
  });
  it("allows cancelling a confirmation and validates Unicode length", () => {
    mount(<NoticeFormScreen />); fillNotice();
    fireEvent.change(screen.getByRole("textbox", { name: "제목" }), { target: { value: "📢".repeat(51) } });
    fireEvent.click(screen.getByRole("button", { name: "발송 내용 확인" }));
    expect(screen.getByRole("alert")).toHaveTextContent("1~50자");
    fireEvent.change(screen.getByRole("textbox", { name: "제목" }), { target: { value: "📢".repeat(50) } });
    fireEvent.click(screen.getByRole("button", { name: "발송 내용 확인" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(navigation.push).not.toHaveBeenCalled();
  });
  it("prevents blind duplicate creation when a response is lost", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/notices", () => { posted(); return HttpResponse.error(); }));
    mount(<NoticeFormScreen />); fillNotice();
    fireEvent.click(screen.getByRole("button", { name: "발송 내용 확인" }));
    fireEvent.click(screen.getByRole("button", { name: "공지 등록 및 즉시 발송" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("등록 여부를 먼저 확인");
    expect(screen.getByRole("link", { name: "공지 목록에서 확인" })).toHaveAttribute("href", "/notices");
    expect(screen.queryByRole("button", { name: "발송 내용 확인" })).not.toBeInTheDocument();
    expect(posted).toHaveBeenCalledTimes(1);
  });
  it("preserves inputs after server validation rejects creation", async () => {
    server.use(http.post("*/api/v1/admin/notices", () => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "제목을 확인해 주세요." }, { status: 400 })));
    mount(<NoticeFormScreen />); fillNotice();
    fireEvent.click(screen.getByRole("button", { name: "발송 내용 확인" }));
    fireEvent.click(screen.getByRole("button", { name: "공지 등록 및 즉시 발송" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("제목을 확인");
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));
    expect(screen.getByRole("textbox", { name: "본문" })).toHaveValue("공지 본문입니다.");
  });
  it("refreshes state if distribution wins the race against an edit", async () => {
    let distributed = false;
    const original = noticeFixtures[0];
    server.use(
      http.get("*/api/v1/admin/notices/:id", () => HttpResponse.json(distributed ? { ...original, status: "DISTRIBUTED", distributedAt: new Date().toISOString() } : original)),
      http.put("*/api/v1/admin/notices/:id", () => { distributed = true; return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "이미 배포되었습니다." }, { status: 400 }); }),
    );
    mount(<NoticeEditScreen noticeId={original.noticeId} />);
    await screen.findByRole("textbox", { name: "제목" });
    fireEvent.click(screen.getByRole("button", { name: "수정 내용 확인" }));
    fireEvent.click(screen.getByRole("button", { name: "수정 저장" }));
    await screen.findByText(/현재 상태: 알림함 배포 완료/);
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
  });
  it("requires a deletion reason and refreshes the cancelled notice", async () => {
    mount(<NoticeDetailScreen noticeId={noticeFixtures[0].noticeId} />);
    fireEvent.click(await screen.findByRole("button", { name: "공지 삭제" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "공지 삭제" })).toBeDisabled();
    fireEvent.change(screen.getByLabelText("삭제 사유"), { target: { value: "일정 취소" } });
    fireEvent.click(within(dialog).getByRole("button", { name: "공지 삭제" }));
    await screen.findByText("발송 취소");
    expect(screen.queryByRole("link", { name: "공지 수정" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "공지 삭제" })).not.toBeInTheDocument();
  });
  it("shows distributed notices read-only and explains push recall limits", async () => {
    mount(<NoticeDetailScreen noticeId={noticeFixtures[1].noticeId} />);
    await screen.findByText("알림함 배포 완료");
    expect(screen.queryByRole("link", { name: "공지 수정" })).not.toBeInTheDocument();
    expect(screen.getByText(/이미 표시된 푸시는 회수되지 않습니다/)).toBeInTheDocument();
  });
});
