import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { aiPromptFixtures } from "@/mocks/ai-prompt-handlers";
import { server } from "@/mocks/server";
import { createQueryClient } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { AiPromptDetailScreen } from "./ai-prompt-detail-screen";
import { AiPromptListScreen } from "./ai-prompt-list-screen";

const navigation = vi.hoisted(() => ({ push: vi.fn(), search: "" }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation, useSearchParams: () => new URLSearchParams(navigation.search) }));
beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
  navigation.push.mockReset(); navigation.search = "";
});
afterEach(() => vi.unstubAllEnvs());
function mount(node: ReactNode) {
  const client = createQueryClient(); client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}><ToastProvider>{node}</ToastProvider></QueryClientProvider>);
}

describe("AI prompt version list", () => {
  it("shows versions newest first with the latest badge on the first row", async () => {
    mount(<AiPromptListScreen />);
    const table = await screen.findByRole("table", { name: "AI 프롬프트 버전 목록" });
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByRole("link", { name: "v2" })).toHaveAttribute("href", `/ai-prompts/${aiPromptFixtures[0].promptVersionId}`);
    expect(within(rows[0]).getByText("최신")).toBeInTheDocument();
    expect(within(rows[0]).getByText("피드백 어조를 부드럽게 조정")).toBeInTheDocument();
    expect(within(rows[0]).getByText("gpt-6.1-sol")).toBeInTheDocument();
    expect(within(rows[0]).getByText("v1", { selector: "td:nth-child(3)" })).toBeInTheDocument();
    expect(within(rows[1]).queryByText("최신")).not.toBeInTheDocument();
    expect(screen.getByText(/PHOTO_REVIEW/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "새 버전 만들기" })).toHaveAttribute("href", "/ai-prompts/new");
  });

  it("does not mark the latest badge on later pages and paginates by hasNext", async () => {
    navigation.search = "page=2";
    server.use(http.get("*/api/v1/admin/ai-prompt-versions", ({ request }) => {
      expect(new URL(request.url).searchParams.get("page")).toBe("2");
      const { systemPrompt: _s, userPromptTemplate: _t, parameters: _p, ...summary } = aiPromptFixtures[1];
      void _s; void _t; void _p;
      return HttpResponse.json({ currentPage: 2, pageSize: 20, hasNext: true, promptVersions: [summary] });
    }));
    mount(<AiPromptListScreen />);
    await screen.findByRole("link", { name: "v1" });
    expect(screen.queryByText("최신")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    expect(navigation.push).toHaveBeenCalledWith("/ai-prompts?page=3");
  });

  it("explains that the feature is off when no version exists", async () => {
    server.use(http.get("*/api/v1/admin/ai-prompt-versions", () => HttpResponse.json({ currentPage: 1, pageSize: 20, hasNext: false, promptVersions: [] })));
    mount(<AiPromptListScreen />);
    expect(await screen.findByText("만든 프롬프트 버전이 없습니다")).toBeInTheDocument();
    expect(screen.getByText(/기능은 꺼진 상태/)).toBeInTheDocument();
  });

  it("shows load errors and retries", async () => {
    server.use(http.get("*/api/v1/admin/ai-prompt-versions", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "권한이 없습니다." }, { status: 403 })));
    mount(<AiPromptListScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    server.resetHandlers();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByRole("link", { name: "v2" })).toBeInTheDocument();
  });
});

describe("AI prompt version detail", () => {
  it("shows prompts, parameters with defaults, and offers a prefilled new version", async () => {
    mount(<AiPromptDetailScreen promptVersionId={aiPromptFixtures[0].promptVersionId} />);
    expect(await screen.findByRole("heading", { name: "프롬프트 v2" })).toBeInTheDocument();
    expect(screen.getByLabelText("시스템 프롬프트 내용")).toHaveTextContent("다정한 어조로 한 문단의 피드백을 작성하세요.");
    expect(screen.getByLabelText("사용자 프롬프트 템플릿 내용")).toHaveTextContent('{"주제": "{{topicTitle}}"}');
    expect(screen.getByText("피드백 어조를 부드럽게 조정")).toBeInTheDocument();
    const imageDetail = screen.getByText("이미지 상세도").closest("div")!;
    expect(imageDetail).toHaveTextContent("HIGH (기본값)");
    expect(screen.getByText("추론 강도").closest("div")).toHaveTextContent("LOW");
    expect(screen.getByText("추론 강도").closest("div")).not.toHaveTextContent("기본값");
    expect(screen.getByRole("link", { name: "이 내용으로 새 버전 만들기" })).toHaveAttribute("href", `/ai-prompts/new?from=${aiPromptFixtures[0].promptVersionId}`);
    expect(screen.queryByRole("button", { name: /삭제|수정/ })).not.toBeInTheDocument();
  });

  it("shows the model default when no reasoning effort is set", async () => {
    mount(<AiPromptDetailScreen promptVersionId={aiPromptFixtures[1].promptVersionId} />);
    await screen.findByRole("heading", { name: "프롬프트 v1" });
    expect(screen.getByText("추론 강도").closest("div")).toHaveTextContent("모델 기본값");
    expect(screen.getByText("메모").closest("div")).toHaveTextContent("-");
  });

  it("shows a not-found state for a missing version", async () => {
    mount(<AiPromptDetailScreen promptVersionId="00000000-0000-4000-8000-000000000000" />);
    expect(await screen.findByRole("alert")).toHaveTextContent("프롬프트 버전을 찾을 수 없습니다");
    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "← 목록으로" })).toHaveAttribute("href", "/ai-prompts");
  });
});
