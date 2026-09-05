import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { topicIds } from "@/mocks/fixtures";
import { QueryProvider } from "@/shared/query/query-provider";
import { ToastProvider } from "@/shared/ui/toast";

import { TopicDetailScreen } from "./topic-detail-screen";
import { TopicForm } from "./topic-form";
import { TopicListScreen } from "./topic-list-screen";

const { routerPush, search } = vi.hoisted(() => ({ routerPush: vi.fn(), search: { value: "phase=BEFORE_OPEN&page=1" } }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/topics",
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => new URLSearchParams(search.value),
}));

describe("topic management screens", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    routerPush.mockReset();
    search.value = "phase=BEFORE_OPEN&page=1";
  });

  it("shows Korean titles and phase filters without topic UUID input", async () => {
    render(<QueryProvider><TopicListScreen /></QueryProvider>);
    expect(await screen.findAllByText("가을을 기다리는 마음")).not.toHaveLength(0);
    expect(screen.getByRole("combobox", { name: "주제 단계" })).toHaveValue("BEFORE_OPEN");
    expect(screen.queryByPlaceholderText("UUID")).not.toBeInTheDocument();
  });

  it("links zero-count topics to the correct filtered posts on desktop and mobile", async () => {
    render(<QueryProvider><TopicListScreen /></QueryProvider>);
    const links = await screen.findAllByRole("link", { name: "검수 대기 게시물 0개 보기" });
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link).toHaveAttribute("href", "/posts?status=PENDING&topicId=" + topicIds.beforeOpen + "&returnTo=%2Ftopics%3Fphase%3DBEFORE_OPEN%26page%3D1");
      expect(link.parentElement?.closest("a")).toBeNull();
    }
    expect(screen.getAllByRole("link", { name: "승인 게시물 0개 보기" })).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: "거절 게시물 0개 보기" })).toHaveLength(2);
  });

  it("links topic detail counts and preserves the source post list", async () => {
    search.value = "returnTo=%2Fposts%3Fstatus%3DAPPROVED";
    render(<QueryProvider><ToastProvider><TopicDetailScreen topicId={topicIds.open} /></ToastProvider></QueryProvider>);
    const link = await screen.findByRole("link", { name: "승인 게시물 11개 보기" });
    const params = new URL(link.getAttribute("href")!, "http://localhost").searchParams;
    expect(params.get("status")).toBe("APPROVED");
    expect(params.get("topicId")).toBe(topicIds.open);
    expect(params.get("returnTo")).toBe("/topics/" + topicIds.open + "?returnTo=%2Fposts%3Fstatus%3DAPPROVED");
    expect(screen.getByRole("link", { name: "← 목록과 필터로 돌아가기" })).toHaveAttribute("href", "/posts?status=APPROVED");
  });

  it("validates the topic date against the Korean start date", async () => {
    const submit = vi.fn();
    const user = userEvent.setup();
    render(<TopicForm pending={false} onSubmit={submit} />);
    await user.type(screen.getByRole("textbox", { name: "주제 제목" }), "새 주제");
    await user.type(screen.getByLabelText("주제 날짜"), "2099-10-02");
    await user.type(screen.getByLabelText("참여 시작"), "2099-10-01T00:00");
    await user.type(screen.getByLabelText("참여 종료"), "2099-10-01T23:59");
    await user.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByText("주제 날짜는 참여 시작일과 같아야 합니다.")).toBeInTheDocument();
    expect(submit).not.toHaveBeenCalled();
  });

  it("requires a reason when deleting a before-open topic", async () => {
    search.value = "returnTo=%2Ftopics%3Fphase%3DBEFORE_OPEN";
    const user = userEvent.setup();
    render(<QueryProvider><ToastProvider><TopicDetailScreen topicId={topicIds.beforeOpen} /></ToastProvider></QueryProvider>);
    await user.click(await screen.findByRole("button", { name: "주제 삭제" }));
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByRole("button", { name: "삭제" })).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox", { name: "삭제 사유" }), "운영 일정 변경");
    await user.click(within(dialog).getByRole("button", { name: "삭제" }));
    expect(await screen.findByText("주제를 삭제했습니다.")).toBeInTheDocument();
    expect(routerPush).toHaveBeenCalledWith("/topics?phase=BEFORE_OPEN");
  });

  it("keeps opened topics read-only", async () => {
    render(<QueryProvider><ToastProvider><TopicDetailScreen topicId={topicIds.open} /></ToastProvider></QueryProvider>);
    expect(await screen.findByText(/공개가 시작된 주제는/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "주제 수정" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "주제 삭제" })).not.toBeInTheDocument();
  });
});
