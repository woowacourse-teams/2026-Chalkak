import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { aiPromptFixtures } from "@/mocks/ai-prompt-handlers";
import { postIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { AiPromptDetailScreen } from "./ai-prompt-detail-screen";
import { AiPromptFormScreen } from "./ai-prompt-form-screen";
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
  return client;
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
    expect(within(rows[0]).getByText("v2", { selector: "td:nth-child(3)" })).toBeInTheDocument();
    expect(within(rows[1]).queryByText("최신")).not.toBeInTheDocument();
    expect(screen.getByText(/PHOTO_REVIEW/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "새 버전 만들기" })).toHaveAttribute("href", "/ai-prompts/new");
    expect(screen.getByRole("link", { name: "AI 프롬프트 처리 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=AI_PROMPT_VERSION");
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
    expect(screen.getByText("버전이 하나도 없으면 사진 AI 리뷰 기능은 꺼진 상태입니다.")).toBeInTheDocument();
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

const createdVersion = { ...aiPromptFixtures[0], promptVersionId: "cccccccc-cccc-4ccc-8ccc-cccccccccc03", version: 3 };
function fillDraft(overrides: { model?: string; postId?: string } = {}) {
  fireEvent.change(screen.getByRole("textbox", { name: "시스템 프롬프트" }), { target: { value: "사진을 따뜻하게 평가하세요." } });
  fireEvent.change(screen.getByRole("textbox", { name: "모델" }), { target: { value: overrides.model ?? "gpt-6-luna" } });
  fireEvent.change(screen.getByRole("textbox", { name: "게시물 ID" }), { target: { value: overrides.postId ?? postIds.approved } });
}
async function runPreview() {
  fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
  return screen.findByRole("region", { name: "미리보기 결과" });
}
const FEEDBACK = {
  summary: "건물의 크기와 높이 차이가 잘 비교되는 구도예요",
  strength: "아래쪽 계단과 겹겹이 쌓인 집들, 위쪽 아파트가 한 화면에 같이 담겼어요.",
  weakness: "맨 아래 담장과 검은 옥상이 골목의 흐름을 끊어요.",
  suggestion: "왼쪽 계단 입구와 붉은 도로는 남기면서 맨 아래 담장이 덜 나오게 담아보세요.",
};
const createButton = () => screen.getByRole("button", { name: "버전 만들기" });

describe("AI prompt form", () => {
  it("starts with defaults, counters and a disabled create button", () => {
    mount(<AiPromptFormScreen />);
    expect(screen.getByRole("textbox", { name: "사용자 프롬프트 템플릿" })).toHaveValue('{"주제": "{{topicTitle}}"}');
    expect(screen.getByRole("combobox", { name: "이미지 상세도" })).toHaveValue("HIGH");
    expect(screen.getByRole("combobox", { name: "추론 강도" })).toHaveValue("");
    expect(screen.getByRole("textbox", { name: "최대 출력 토큰" })).toHaveValue("8000");
    expect(screen.getByRole("textbox", { name: "독특한 시각 기준 점수" })).toHaveValue("7.0");
    expect(screen.getByText("0 / 50,000자")).toBeInTheDocument();
    expect(screen.getByText("0 / 500자 · 앞뒤 공백 제외")).toBeInTheDocument();
    expect(createButton()).toBeDisabled();
    expect(screen.getByText("성공한 미리보기를 실행한 뒤에 버전을 만들 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /승인된 게시물 목록/ })).toHaveAttribute("target", "_blank");
    fireEvent.change(screen.getByRole("textbox", { name: "시스템 프롬프트" }), { target: { value: "가나다" } });
    expect(screen.getByText("3 / 50,000자")).toBeInTheDocument();
  });

  it("shows validation errors without calling the preview API", async () => {
    const called = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", () => { called(); return HttpResponse.json({}); }));
    mount(<AiPromptFormScreen />);
    fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
    expect(screen.getByText("시스템 프롬프트를 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("모델을 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("미리보기할 게시물 ID를 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "시스템 프롬프트" })).toHaveFocus();

    fillDraft({ postId: "not-a-uuid" });
    fireEvent.change(screen.getByRole("textbox", { name: "시스템 프롬프트" }), { target: { value: "주제는 {{topicTitle}}" } });
    fireEvent.change(screen.getByRole("textbox", { name: "사용자 프롬프트 템플릿" }), { target: { value: "{{other}}" } });
    fireEvent.change(screen.getByRole("textbox", { name: "최대 출력 토큰" }), { target: { value: "16001" } });
    fireEvent.change(screen.getByRole("textbox", { name: "독특한 시각 기준 점수" }), { target: { value: "10.5" } });
    fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
    expect(screen.getByText("시스템 프롬프트에는 {{...}} 형식의 변수를 사용할 수 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("사용할 수 있는 변수는 {{topicTitle}} 하나뿐입니다.")).toBeInTheDocument();
    expect(screen.getByText(/최대 출력 토큰은 1~16,000/)).toBeInTheDocument();
    expect(screen.getByText(/독특한 시각 기준 점수는 0~10/)).toBeInTheDocument();
    expect(screen.getByText("게시물 ID는 UUID 형식으로 입력해 주세요.")).toBeInTheDocument();
    expect(called).not.toHaveBeenCalled();
  });

  it.each([["최대 출력 토큰", "e"], ["최대 출력 토큰", "1.5"], ["독특한 시각 기준 점수", "7."], ["독특한 시각 기준 점수", "abc"]])(
    "shows an error for partial %s input %j instead of falling back to the default", (label, value) => {
      const called = vi.fn();
      server.use(http.post("*/api/v1/admin/ai-prompt-previews", () => { called(); return HttpResponse.json({}); }));
      mount(<AiPromptFormScreen />);
      fillDraft();
      fireEvent.change(screen.getByRole("textbox", { name: label }), { target: { value } });
      fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
      expect(screen.getByRole("textbox", { name: label })).toHaveAttribute("aria-invalid", "true");
      expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
      expect(called).not.toHaveBeenCalled();
    });

  it("treats empty number fields as server defaults", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", async ({ request }) => { posted(await request.json()); return HttpResponse.json({ succeeded: false, failureType: "REFUSED", retryable: false, result: null, output: null, usage: null, latencyMs: null, responseId: null }); }));
    mount(<AiPromptFormScreen />);
    fillDraft();
    fireEvent.change(screen.getByRole("textbox", { name: "최대 출력 토큰" }), { target: { value: "" } });
    fireEvent.change(screen.getByRole("textbox", { name: "독특한 시각 기준 점수" }), { target: { value: "" } });
    await runPreview();
    expect(posted).toHaveBeenCalledWith(expect.objectContaining({ parameters: { imageDetail: "HIGH" } }));
  });

  it("shows a successful preview, blocks double runs, and sends the draft as typed numbers", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", async ({ request }) => {
      posted(await request.json());
      return HttpResponse.json({
        succeeded: true, failureType: null, retryable: null,
        result: { overallScore: 8.2, criteria: [{ key: "COMPOSITION", label: "구도", score: 8.2 }], distinctivePerspective: true, feedback: FEEDBACK },
        output: { compositionScore: 8.2, themeExpressionScore: 7.4 },
        usage: { inputTokens: 1450, cachedInputTokens: null, outputTokens: 620, reasoningTokens: 410 }, latencyMs: 4200, responseId: "resp_1",
      });
    }));
    mount(<AiPromptFormScreen />);
    fillDraft();
    fireEvent.change(screen.getByRole("textbox", { name: "최대 출력 토큰" }), { target: { value: "4000" } });
    fireEvent.change(screen.getByRole("textbox", { name: "독특한 시각 기준 점수" }), { target: { value: "6.5" } });
    fireEvent.change(screen.getByRole("combobox", { name: "추론 강도" }), { target: { value: "LOW" } });
    fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
    expect(screen.getByRole("button", { name: "미리보기 실행 중…" })).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("최대 1분");
    const result = await screen.findByRole("region", { name: "미리보기 결과" });
    expect(posted).toHaveBeenCalledExactlyOnceWith({
      type: "PHOTO_REVIEW", systemPrompt: "사진을 따뜻하게 평가하세요.", userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
      parameters: { imageDetail: "HIGH", maxOutputTokens: 4000, reasoningEffort: "LOW", distinctivePerspectiveThreshold: 6.5 }, postId: postIds.approved,
    });
    expect(within(result).getByText("미리보기 성공")).toBeInTheDocument();
    expect(within(result).getByText("종합 점수").nextSibling).toHaveTextContent("8.2");
    expect(within(result).getByText("구도").nextSibling).toHaveTextContent("8.2");
    expect(within(result).getByText("독특한 시각").nextSibling).toHaveTextContent("예");
    expect(within(result).getByRole("heading", { name: FEEDBACK.summary })).toBeInTheDocument();
    expect(within(result).getByText("좋았던 점").nextSibling).toHaveTextContent(FEEDBACK.strength);
    expect(within(result).getByText("아쉬운 점").nextSibling).toHaveTextContent(FEEDBACK.weakness);
    expect(within(result).getByText("다음엔 이렇게").nextSibling).toHaveTextContent(FEEDBACK.suggestion);
    expect(within(result).getByText("모델 원점수 · 주제 표현").nextSibling).toHaveTextContent("7.4");
    expect(within(result).getByText("입력 토큰").nextSibling).toHaveTextContent("1,450토큰");
    expect(within(result).getByText("캐시된 입력 토큰").nextSibling).toHaveTextContent("미보고");
    expect(within(result).getByText("응답 시간").nextSibling).toHaveTextContent("4.2초");
    expect(within(result).getByText("응답 ID").nextSibling).toHaveTextContent("resp_1");
    expect(createButton()).toBeEnabled();
  });

  it("still shows a plain-string feedback from a backend that has not deployed the sectioned format", async () => {
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", () => HttpResponse.json({
      succeeded: true, failureType: null, retryable: null,
      result: { overallScore: 8.2, criteria: [{ key: "COMPOSITION", label: "구도", score: 8.2 }], distinctivePerspective: true, feedback: "첫 줄\n둘째 줄" },
      output: { compositionScore: 8.2, themeExpressionScore: 7.4 },
      usage: null, latencyMs: 4200, responseId: "resp_legacy",
    })));
    mount(<AiPromptFormScreen />);
    fillDraft();
    const result = await runPreview();
    expect(within(result).getByText(/첫 줄/).textContent).toBe("첫 줄\n둘째 줄");
    expect(within(result).queryByText("좋았던 점")).not.toBeInTheDocument();
  });

  it.each([
    ["mock-timeout", "시간 초과", "다시 시도할 수 있습니다", "재시도 가능 여부: 예"],
    ["mock-quota", "사용 한도 소진", "입력이나 설정을 확인해 주세요", "재시도 가능 여부: 아니오"],
    ["mock-invalid-output", "출력 형식 오류", "입력이나 설정을 확인해 주세요", "재시도 가능 여부: 아니오"],
  ])("shows a succeeded:false preview as a failure for %s and keeps create disabled", async (model, label, guidance, retry) => {
    mount(<AiPromptFormScreen />);
    fillDraft({ model });
    const result = await runPreview();
    expect(within(result).getByText("미리보기 실패")).toBeInTheDocument();
    expect(within(result).getByText(label)).toBeInTheDocument();
    expect(within(result).getByText(new RegExp(guidance))).toBeInTheDocument();
    expect(within(result).getByText(retry)).toBeInTheDocument();
    expect(within(result).getByText("입력 토큰").nextSibling).toHaveTextContent("-");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(createButton()).toBeDisabled();
  });

  it("shows usage and response id on an incomplete failure", async () => {
    mount(<AiPromptFormScreen />);
    fillDraft({ model: "mock-incomplete" });
    const result = await runPreview();
    expect(within(result).getByText("출력 잘림")).toBeInTheDocument();
    expect(within(result).getByText("추론 토큰").nextSibling).toHaveTextContent("7,600토큰");
    expect(within(result).getByText("캐시된 입력 토큰").nextSibling).toHaveTextContent("미보고");
    expect(within(result).getByText("응답 ID").nextSibling).toHaveTextContent("resp_mock_failed");
  });

  it.each([
    [{ model: "unknown-model" }, "허용되지 않은 모델입니다."],
    [{ postId: postIds.pending }, "공개된 게시물을 찾을 수 없습니다."],
  ])("shows the server message inline for HTTP errors %j", async (overrides, message) => {
    mount(<AiPromptFormScreen />);
    fillDraft(overrides);
    fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByRole("region", { name: "미리보기 결과" })).not.toBeInTheDocument();
    expect(createButton()).toBeDisabled();
  });

  it("shows a connection message when the preview request cannot complete", async () => {
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", () => HttpResponse.error()));
    mount(<AiPromptFormScreen />);
    fillDraft();
    fireEvent.click(screen.getByRole("button", { name: "미리보기 실행" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다.");
  });

  it("requires a fresh preview after the draft changes but not after memo or post changes", async () => {
    mount(<AiPromptFormScreen />);
    fillDraft();
    await runPreview();
    expect(createButton()).toBeEnabled();
    fireEvent.change(screen.getByRole("textbox", { name: "메모 (선택)" }), { target: { value: "메모만 변경" } });
    fireEvent.change(screen.getByRole("textbox", { name: "게시물 ID" }), { target: { value: postIds.rejected } });
    expect(createButton()).toBeEnabled();

    fireEvent.change(screen.getByRole("textbox", { name: "시스템 프롬프트" }), { target: { value: "다르게 평가하세요." } });
    expect(createButton()).toBeDisabled();
    expect(screen.getAllByText(/다시 미리보기를 실행/).length).toBeGreaterThan(0);
    fireEvent.change(screen.getByRole("textbox", { name: "시스템 프롬프트" }), { target: { value: "사진을 따뜻하게 평가하세요." } });
    expect(createButton()).toBeEnabled();

    fireEvent.change(screen.getByRole("combobox", { name: "이미지 상세도" }), { target: { value: "LOW" } });
    expect(createButton()).toBeDisabled();
    fireEvent.change(screen.getByRole("textbox", { name: "게시물 ID" }), { target: { value: postIds.approved } });
    await runPreview();
    expect(createButton()).toBeEnabled();
  });

  it("confirms the newest-version effect, creates once, refreshes caches and opens the new version", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-versions", async ({ request }) => { posted(await request.json()); return HttpResponse.json(createdVersion, { status: 201 }); }));
    const client = mount(<AiPromptFormScreen />);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    fillDraft();
    fireEvent.change(screen.getByRole("textbox", { name: "메모 (선택)" }), { target: { value: "  톤 조정  " } });
    await runPreview();
    fireEvent.click(createButton());
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("가장 최신 버전");
    expect(dialog).toHaveTextContent("수정하거나 삭제할 수 없습니다");
    fireEvent.click(within(dialog).getByRole("button", { name: "만들기" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith(`/ai-prompts/${createdVersion.promptVersionId}`));
    expect(posted).toHaveBeenCalledExactlyOnceWith({
      type: "PHOTO_REVIEW", systemPrompt: "사진을 따뜻하게 평가하세요.", userPromptTemplate: '{"주제": "{{topicTitle}}"}', model: "gpt-6-luna",
      parameters: { imageDetail: "HIGH", maxOutputTokens: 8000, distinctivePerspectiveThreshold: 7 }, memo: "톤 조정",
    });
    expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.aiPrompts });
    expect(await screen.findByText("프롬프트 v3을(를) 만들었습니다.")).toBeInTheDocument();
  });

  it("shows server 400 messages inside the dialog and keeps the draft", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-versions", () => { posted(); return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "허용되지 않은 모델입니다." }, { status: 400 }); }));
    mount(<AiPromptFormScreen />);
    fillDraft();
    await runPreview();
    fireEvent.click(createButton());
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "만들기" }));
    expect(await within(screen.getByRole("dialog")).findByRole("alert")).toHaveTextContent("허용되지 않은 모델입니다.");
    expect(posted).toHaveBeenCalledOnce();
    expect(navigation.push).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "모델" })).toHaveValue("gpt-6-luna");
  });

  it.each([500, 502])("does not retry an uncertain create after HTTP %i and points to the list", async (status) => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-versions", () => { posted(); return HttpResponse.json({ errorCode: "ADMIN_UPSTREAM_ERROR", message: "서버 오류" }, { status }); }));
    mount(<AiPromptFormScreen />);
    fillDraft();
    await runPreview();
    fireEvent.click(createButton());
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "만들기" }));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("만들어졌을 수 있습니다");
    expect(within(alert).getByRole("link", { name: "버전 목록을 확인해 주세요." })).toHaveAttribute("href", "/ai-prompts");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(createButton()).toBeDisabled();
    expect(posted).toHaveBeenCalledOnce();
  });

  it("prefills from an existing version without copying the memo", async () => {
    navigation.search = `from=${aiPromptFixtures[0].promptVersionId}`;
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/ai-prompt-previews", async ({ request }) => { posted(await request.json()); return HttpResponse.json({ succeeded: false, failureType: "REFUSED", retryable: false, result: null, output: null, usage: null, latencyMs: 900, responseId: null }); }));
    mount(<AiPromptFormScreen />);
    expect(await screen.findByText("v2 내용을 불러왔습니다. 메모는 새로 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "시스템 프롬프트" })).toHaveValue(aiPromptFixtures[0].systemPrompt);
    expect(screen.getByRole("textbox", { name: "모델" })).toHaveValue("gpt-6.1-sol");
    expect(screen.getByRole("combobox", { name: "추론 강도" })).toHaveValue("LOW");
    expect(screen.getByRole("textbox", { name: "메모 (선택)" })).toHaveValue("");
    expect(createButton()).toBeDisabled();
    fireEvent.change(screen.getByRole("textbox", { name: "게시물 ID" }), { target: { value: postIds.approved } });
    await runPreview();
    expect(posted).toHaveBeenCalledWith(expect.objectContaining({ model: "gpt-6.1-sol", parameters: expect.objectContaining({ reasoningEffort: "LOW" }) }));
  });

  it("explains a missing source version and starts from a blank form on request", async () => {
    navigation.search = "from=00000000-0000-4000-8000-000000000000";
    mount(<AiPromptFormScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("불러올 버전을 찾을 수 없습니다");
    expect(screen.getByRole("link", { name: "빈 양식으로 시작" })).toHaveAttribute("href", "/ai-prompts/new");
  });

  it("ignores a malformed source id with a notice", () => {
    navigation.search = "from=nope";
    mount(<AiPromptFormScreen />);
    expect(screen.getByText(/불러올 버전 ID가 올바르지 않아/)).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "시스템 프롬프트" })).toHaveValue("");
  });
});
