import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { feedbackIds, userIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import { QueryProvider } from "@/shared/query/query-provider";

import { FeedbackDetailScreen } from "./feedback-detail-screen";

const search = vi.hoisted(() => ({ value: "returnTo=%2Ffeedbacks%3Fpage%3D2" }));
vi.mock("next/navigation", () => ({
  useSearchParams: () => new URLSearchParams(search.value),
}));

function mount(feedbackId: string) {
  render(<QueryProvider><FeedbackDetailScreen feedbackId={feedbackId} /></QueryProvider>);
  return userEvent.setup();
}

async function confirm(user: ReturnType<typeof userEvent.setup>, action: string) {
  await user.click(await screen.findByRole("button", { name: action }));
  const dialog = await screen.findByRole("dialog");
  await user.click(within(dialog).getByRole("button", { name: action }));
}

describe("admin feedback detail", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    search.value = "returnTo=%2Ffeedbacks%3Fpage%3D2";
  });

  it("links back to the profile history and keeps the author link on the same profile", async () => {
    const returnTo = "/users/" + userIds.active + "?feedbackPage=2#feedback-history";
    search.value = new URLSearchParams({ returnTo }).toString();
    mount(feedbackIds.newest);
    expect(await screen.findByRole("link", { name: "creator@example.com" })).toHaveAttribute("href", returnTo);
    expect(screen.getByRole("link", { name: /사용자 문의 내역으로 돌아가기/ })).toHaveAttribute("href", returnTo);
    expect(screen.queryByRole("link", { name: "이 사용자의 문의" })).not.toBeInTheDocument();
  });

  it("registers a first answer without a push override and refreshes the detail", async () => {
    const bodies: unknown[] = [];
    server.use(http.post("*/api/v1/admin/feedbacks/:id/answer", async ({ request }) => {
      bodies.push(await request.clone().json());
    }));
    const user = mount(feedbackIds.newest);
    const submit = await screen.findByRole("button", { name: "답변 등록" });
    expect(submit).toBeDisabled();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: /문의 목록으로 돌아가기/ })).toHaveAttribute("href", "/feedbacks?page=2");

    await user.type(screen.getByRole("textbox", { name: "답변 내용" }), "  확인했습니다.  ");
    await user.click(submit);
    expect(await screen.findByText("답변 완료")).toBeInTheDocument();
    expect(bodies).toEqual([{ answerContent: "확인했습니다." }]);
  });

  it.each([false, true])("deletes a specific answer and registers a new one with opt-in %s", async (optIn) => {
    const versions: Array<string | null> = [];
    const bodies: unknown[] = [];
    server.use(
      http.delete("*/api/v1/admin/feedbacks/:id/answer", ({ request }) => {
        versions.push(new URL(request.url).searchParams.get("answerVersion"));
      }),
      http.post("*/api/v1/admin/feedbacks/:id/answer", async ({ request }) => {
        bodies.push(await request.clone().json());
      }),
    );
    const user = mount(feedbackIds.middle);
    await confirm(user, "답변 삭제");
    const push = await screen.findByRole("checkbox", { name: "재답변 푸시 보내기" });
    expect(push).not.toBeChecked();
    expect(versions).toEqual(["demo-initial-answer"]);
    if (optIn) await user.click(push);
    await user.type(screen.getByRole("textbox", { name: "답변 내용" }), "새 답변입니다.");
    await user.click(screen.getByRole("button", { name: "재답변 등록" }));
    expect(await screen.findByText("답변 완료")).toBeInTheDocument();
    expect(bodies).toEqual([optIn ? { answerContent: "새 답변입니다.", sendPush: true } : { answerContent: "새 답변입니다." }]);
  });

  it("refreshes detail when the answer version is rejected", async () => {
    let changed = false;
    server.use(
      http.get("*/api/v1/admin/feedbacks/:id", () => {
        if (!changed) return;
        return HttpResponse.json({
          feedbackId: feedbackIds.middle,
          content: "문의 내용",
          createdAt: "2026-09-15T09:30:00Z",
          deletedAt: null,
          author: { userId: "user", email: null, status: "ACTIVE", appVersion: null },
          answerContent: "다른 관리자가 등록한 답변",
          answeredAt: "2026-09-16T02:10:00Z",
          answerState: "ANSWERED",
          answerVersion: "new-version",
        });
      }),
      http.delete("*/api/v1/admin/feedbacks/:id/answer", () => {
        changed = true;
        return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "버전이 다릅니다." }, { status: 400 });
      }),
    );
    const user = mount(feedbackIds.middle);
    await confirm(user, "답변 삭제");
    expect(await screen.findByRole("alert")).toHaveTextContent("갱신된 문의 상세");
    expect(screen.getByText("다른 관리자가 등록한 답변")).toBeInTheDocument();
  });

  it("retains the inquiry record after whole inquiry deletion and hides answer actions", async () => {
    const user = mount(feedbackIds.newest);
    await confirm(user, "문의 삭제");
    expect(await screen.findByText("삭제된 문의")).toBeInTheDocument();
    expect(screen.getByText(/사진 업로드 버튼/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "답변 등록" })).not.toBeInTheDocument();
  });
});
