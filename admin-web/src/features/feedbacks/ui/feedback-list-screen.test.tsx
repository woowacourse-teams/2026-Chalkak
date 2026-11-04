import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, onTestFinished, vi } from "vitest";

import { feedbackFixtures, feedbackIds, userIds } from "@/mocks/fixtures";
import { server } from "@/mocks/server";
import { QueryProvider } from "@/shared/query/query-provider";

import { FeedbackListScreen } from "./feedback-list-screen";

const { routerPush, search } = vi.hoisted(() => ({
  routerPush: vi.fn(),
  search: { value: "" },
}));
vi.mock("next/navigation", () => ({
  usePathname: () => "/feedbacks",
  useRouter: () => ({ push: routerPush }),
  useSearchParams: () => new URLSearchParams(search.value),
}));

function mount() {
  render(<QueryProvider><FeedbackListScreen /></QueryProvider>);
}

async function entries() {
  const list = await screen.findByRole("list", { name: "사용자 피드백" });
  return within(list).getAllByRole("listitem");
}

function pushedParams() {
  const [href] = routerPush.mock.lastCall as [string];
  return new URL(href, "http://localhost").searchParams;
}

describe("feedback list screen", () => {
  beforeEach(() => {
    vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
    vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  });
  afterEach(() => {
    vi.unstubAllEnvs();
    routerPush.mockReset();
    search.value = "";
  });

  it("shows the newest feedback first with its author and preserved line breaks", async () => {
    mount();
    const [newest] = await entries();

    const author = within(newest).getByRole("link", { name: "creator@example.com" });
    expect(author).toHaveAttribute("href", "/users/" + userIds.active + "?returnTo=%2Ffeedbacks");
    expect(within(newest).getByText("활성")).toBeInTheDocument();
    expect(within(newest).getByText("앱 1.4.0")).toBeInTheDocument();
    expect(feedbackFixtures[0].content).toContain("\n");
    expect(within(newest).getByText(feedbackFixtures[0].content, { normalizer: (text) => text })).toBeInTheDocument();
  });

  it("keeps feedback from banned and withdrawn authors readable", async () => {
    mount();
    const [, banned, withdrawn] = await entries();

    expect(within(banned).getByRole("link", { name: "paused@example.com" })).toBeInTheDocument();
    expect(within(banned).getByText("차단")).toBeInTheDocument();
    expect(within(withdrawn).getByRole("link", { name: "이메일 정보 없음" })).toHaveAttribute(
      "href", "/users/" + userIds.withdrawn + "?returnTo=%2Ffeedbacks",
    );
    expect(within(withdrawn).getByText("탈퇴")).toBeInTheDocument();
    expect(within(withdrawn).getByText("앱 —")).toBeInTheDocument();
  });

  it("shows answer badges alongside inquiry deletion badges", async () => {
    mount();
    const [unanswered, answered, deleted] = await entries();
    expect(within(unanswered).getByText("미답변")).toBeInTheDocument();
    expect(within(answered).getByText("답변 완료")).toBeInTheDocument();
    expect(within(deleted).getByText("삭제된 문의")).toBeInTheDocument();
  });

  it("requests combined filters from the server and displays the returned page", async () => {
    search.value = "inquiryStatus=ANSWERED&bannedAuthorOnly=true&pageSize=1";
    const requested: URLSearchParams[] = [];
    const record = ({ request }: { request: Request }) => {
      if (new URL(request.url).pathname.endsWith("/feedbacks")) requested.push(new URL(request.url).searchParams);
    };
    server.events.on("request:start", record);
    onTestFinished(() => server.events.removeListener("request:start", record));
    mount();
    const rows = await entries();
    expect(rows).toHaveLength(1);
    expect(within(rows[0]).getByRole("link", { name: "paused@example.com" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "답변 완료" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("checkbox", { name: "차단 회원만" })).toBeChecked();
    expect(requested.at(-1)?.get("inquiryStatus")).toBe("ANSWERED");
    expect(requested.at(-1)?.get("bannedAuthorOnly")).toBe("true");
    expect(screen.getByRole("button", { name: "다음" })).toBeDisabled();
  });

  it("resets pagination when changing status and keeps the banned-author condition", async () => {
    search.value = "page=3&bannedAuthorOnly=true&inquiryStatus=ANSWERED";
    const user = userEvent.setup();
    mount();
    await user.click(screen.getByRole("button", { name: "삭제된 문의" }));
    expect(pushedParams().get("inquiryStatus")).toBe("INQUIRY_DELETED");
    expect(pushedParams().get("bannedAuthorOnly")).toBe("true");
    expect(pushedParams().get("page")).toBe("1");
  });

  it("resets pagination when toggling the banned-author filter and keeps the selected status", async () => {
    search.value = "page=3&inquiryStatus=NEEDS_ANSWER";
    const user = userEvent.setup();
    mount();
    await user.click(screen.getByRole("checkbox", { name: "차단 회원만" }));
    expect(pushedParams().get("inquiryStatus")).toBe("NEEDS_ANSWER");
    expect(pushedParams().get("bannedAuthorOnly")).toBe("true");
    expect(pushedParams().get("page")).toBe("1");
  });

  it("requests the oldest first when the URL asks for it", async () => {
    search.value = "sort=createdAtAsc";
    const requested: URLSearchParams[] = [];
    const record = ({ request }: { request: Request }) => {
      if (new URL(request.url).pathname.endsWith("/feedbacks")) requested.push(new URL(request.url).searchParams);
    };
    server.events.on("request:start", record);
    onTestFinished(() => server.events.removeListener("request:start", record));
    mount();
    const [oldest] = await entries();

    expect(within(oldest).getByText("탈퇴")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "정렬" })).toHaveValue("createdAtAsc");
    expect(requested.at(-1)?.get("sort")).toBe("createdAtAsc");
    expect(requested.at(-1)?.get("page")).toBe("1");
    expect(requested.at(-1)?.get("pageSize")).toBe("20");
  });

  it("falls back to the newest order for an unknown sort", async () => {
    search.value = "sort=likesDesc";
    mount();
    const [first] = await entries();

    expect(within(first).getByText("활성")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "정렬" })).toHaveValue("createdAtDesc");
  });

  it("changes the order through the URL and returns to the first page", async () => {
    search.value = "page=2";
    const user = userEvent.setup();
    mount();
    await screen.findByRole("combobox", { name: "정렬" });

    await user.selectOptions(screen.getByRole("combobox", { name: "정렬" }), "createdAtAsc");

    expect(pushedParams().get("sort")).toBe("createdAtAsc");
    expect(pushedParams().get("page")).toBe("1");
  });

  it("moves to the next page while keeping the chosen order", async () => {
    search.value = "sort=createdAtAsc";
    server.use(http.get("*/api/v1/admin/feedbacks", () => HttpResponse.json({
      currentPage: 1, pageSize: 20, hasNext: true, feedbacks: [],
    })));
    const user = userEvent.setup();
    mount();

    await user.click(await screen.findByRole("button", { name: "다음" }));

    expect(pushedParams().get("page")).toBe("2");
    expect(pushedParams().get("sort")).toBe("createdAtAsc");
  });

  it("shows an empty state when nothing has been submitted", async () => {
    server.use(http.get("*/api/v1/admin/feedbacks", () => HttpResponse.json({
      currentPage: 1, pageSize: 20, hasNext: false, feedbacks: [],
    })));
    mount();

    expect(await screen.findByRole("heading", { name: "접수된 피드백이 없습니다" })).toBeInTheDocument();
    expect(screen.queryByRole("list", { name: "사용자 피드백" })).not.toBeInTheDocument();
  });

  it("shows the server message when access is denied", async () => {
    server.use(http.get("*/api/v1/admin/feedbacks", () => HttpResponse.json(
      { errorCode: "FORBIDDEN", message: "관리자 권한이 없습니다." }, { status: 403 },
    )));
    mount();

    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("관리자 권한이 없습니다."));
  });

  it("links to the user profile and inquiry detail without a separate history button", async () => {
    search.value = "sort=createdAtAsc&page=1";
    mount();
    const rows = await entries();
    const newest = rows.at(-1)!;
    expect(within(newest).getByRole("link", { name: "creator@example.com" })).toHaveAttribute(
      "href", "/users/" + userIds.active + "?returnTo=%2Ffeedbacks%3Fsort%3DcreatedAtAsc%26page%3D1",
    );
    const href = within(newest).getByRole("link", { name: "문의 상세" }).getAttribute("href") ?? "";
    const detail = new URL(href, "http://localhost");
    expect(detail.pathname).toBe("/feedbacks/" + feedbackIds.newest);
    expect(detail.searchParams.get("returnTo")).toBe("/feedbacks?" + search.value);
    expect(screen.queryByRole("button", { name: "이 사용자의 문의" })).not.toBeInTheDocument();
  });

  it("keeps write actions on the detail screen", async () => {
    mount();
    const list = await screen.findByRole("list", { name: "사용자 피드백" });

    expect(within(list).queryByRole("button", { name: /삭제|답변|처리/ })).not.toBeInTheDocument();
  });
});
