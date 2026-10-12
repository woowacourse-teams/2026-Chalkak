import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { ScheduleScreen } from "./schedule-screen";

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
});
afterEach(() => vi.unstubAllEnvs());

function mount() {
  const client = createQueryClient();
  client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}><ToastProvider><ScheduleScreen /></ToastProvider></QueryClientProvider>);
  return client;
}

describe("topic push schedules", () => {
  it("shows recurring KST times and links to setting history", async () => {
    mount();
    const list = await screen.findByRole("list", { name: "주제 알림 발송 시간" });
    expect(within(list).getAllByRole("listitem").map((row) => row.textContent)).toEqual(["09:00매일 · KST수정삭제", "18:30매일 · KST수정삭제"]);
    expect(screen.getByRole("link", { name: "설정 변경 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=TOPIC_PUSH_SCHEDULE");
    expect(screen.getByText(/저장 즉시 발송하지 않습니다/)).toBeInTheDocument();
  });

  it("creates a minute time, sorts the refreshed list, and invalidates audit history", async () => {
    const client = mount();
    client.setQueryData(queryKeys.auditLogs, { auditLogs: [] });
    await screen.findByText("09:00");
    fireEvent.click(screen.getByRole("button", { name: "발송 시간 추가" }));
    fireEvent.change(screen.getByLabelText("발송 시간 (KST)"), { target: { value: "07:15" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByText("07:15");
    expect(await screen.findByText("발송 시간을 저장했습니다.")).toBeInTheDocument();
    expect(client.getQueryState(queryKeys.auditLogs)?.isInvalidated).toBe(true);
    expect(within(screen.getByRole("list")).getAllByRole("listitem")[0]).toHaveTextContent("07:15");
  });

  it("keeps duplicate input and its server error visible", async () => {
    mount();
    await screen.findByText("09:00");
    fireEvent.click(screen.getByRole("button", { name: "발송 시간 추가" }));
    fireEvent.change(screen.getByLabelText("발송 시간 (KST)"), { target: { value: "09:00" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("같은 발송 시각");
    expect(screen.getByLabelText("발송 시간 (KST)")).toHaveValue("09:00");
  });

  it("updates an existing time and supports midnight", async () => {
    mount();
    await screen.findByText("09:00");
    fireEvent.click(screen.getByRole("button", { name: "09:00 수정" }));
    expect(screen.getByLabelText("발송 시간 (KST)")).toHaveValue("09:00");
    fireEvent.change(screen.getByLabelText("발송 시간 (KST)"), { target: { value: "00:00" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    await screen.findByText("00:00");
    await waitFor(() => expect(screen.queryByText("09:00")).not.toBeInTheDocument());
  });

  it("cancels deletion or confirms it without a reason or request body", async () => {
    mount();
    await screen.findByText("09:00");
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "09:00 삭제" }));
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "취소" }));
    expect(screen.getByText("09:00")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "09:00 삭제" }));
    await user.click(screen.getByRole("button", { name: "발송 시간 삭제" }));
    await waitFor(() => expect(screen.queryByText("09:00")).not.toBeInTheDocument());
    expect(screen.getByText("18:30")).toBeInTheDocument();
  });

  it("retains the confirmation dialog on a failed delete", async () => {
    server.use(http.delete("*/api/v1/admin/topic-push-schedules/:id", () => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "삭제에 실패했습니다." }, { status: 500 })));
    mount();
    await screen.findByText("09:00");
    fireEvent.click(screen.getByRole("button", { name: "09:00 삭제" }));
    fireEvent.click(screen.getByRole("button", { name: "발송 시간 삭제" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("삭제에 실패했습니다.");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("09:00")).toBeInTheDocument();
  });

  it("shows loading, empty, and retryable failure states", async () => {
    server.use(http.get("*/api/v1/admin/topic-push-schedules", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "관리자 권한이 없습니다." }, { status: 403 })));
    mount();
    expect(screen.getByLabelText("콘텐츠 불러오는 중")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("관리자 권한이 없습니다.");
    server.use(http.get("*/api/v1/admin/topic-push-schedules", () => HttpResponse.json({ schedules: [] })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await screen.findByRole("heading", { name: "등록된 발송 시간이 없습니다" });
  });
});
