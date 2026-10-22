import { QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ReactNode } from "react";
import { server } from "@/mocks/server";
import { bannerFixtures } from "@/mocks/banner-handlers";
import { createQueryClient } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { BannerListScreen } from "./banner-list-screen";
import { BannerFormScreen } from "./banner-form-screen";

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
function fill() {
  for (const [label, value] of [["제목", "새 배너"], ["부제목", "부제목"], ["웹뷰 URL", "https://example.com/event?source=home#top"], ["썸네일 URL", "https://example.com/image.png"]]) fireEvent.change(screen.getByRole("textbox", { name: label }), { target: { value } });
}
describe("banner screens", () => {
  it("shows thumbnails, public links, create and audit navigation", async () => {
    mount(<BannerListScreen />);
    expect(await screen.findByText(bannerFixtures[0].title)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "배너 등록" })).toHaveAttribute("href", "/banners/new");
    expect(screen.getByRole("link", { name: "배너 처리 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=BANNER");
    expect(screen.getAllByRole("link", { name: /웹뷰 열기/ })[0]).toHaveAttribute("rel", "noopener noreferrer");
    fireEvent.error(screen.getByAltText(`${bannerFixtures[0].title} 썸네일`));
    expect(screen.getByText("이미지를 불러올 수 없습니다")).toBeInTheDocument();
  });
  it("uses API pagination and returns to the previous page after deleting its last item", async () => {
    navigation.search = "page=2";
    server.use(http.get("*/api/v1/admin/banners", () => HttpResponse.json({ currentPage: 2, pageSize: 20, hasNext: true, banners: [bannerFixtures[0]] })));
    mount(<BannerListScreen />);
    await screen.findByText(bannerFixtures[0].title);
    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    expect(navigation.push).toHaveBeenCalledWith("/banners?page=3");
    fireEvent.click(screen.getByRole("button", { name: `${bannerFixtures[0].title} 삭제` }));
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "배너 삭제" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/banners?page=1"));
  });
  it("shows load errors and retries into the empty state", async () => {
    server.use(http.get("*/api/v1/admin/banners", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "권한이 없습니다." }, { status: 403 })));
    mount(<BannerListScreen />);
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    server.use(http.get("*/api/v1/admin/banners", () => HttpResponse.json({ currentPage: 1, pageSize: 20, hasNext: false, banners: [] })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await screen.findByText("표시할 배너가 없습니다");
  });
  it("validates fields before allowing registration", () => {
    mount(<BannerFormScreen />); fireEvent.click(screen.getByRole("button", { name: "배너 등록" }));
    expect(screen.getByText("제목을(를) 1~50자로 입력해 주세요.")).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("confirms immediate publication and submits all four fields once", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/banners", async ({ request }) => { posted(await request.json()); return HttpResponse.json(bannerFixtures[0], { status: 201 }); }));
    mount(<BannerFormScreen />); fill(); fireEvent.click(screen.getByRole("button", { name: "배너 등록" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("등록 즉시 앱에 공개됩니다");
    fireEvent.click(screen.getByRole("button", { name: "등록하기" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/banners"));
    expect(posted).toHaveBeenCalledExactlyOnceWith({ title: "새 배너", subtitle: "부제목", webViewUrl: "https://example.com/event?source=home#top", thumbnailUrl: "https://example.com/image.png" });
  });
  it("shows duplicate errors without automatic retries and keeps the form", async () => {
    const posted = vi.fn();
    server.use(http.post("*/api/v1/admin/banners", () => { posted(); return HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "동일한 웹뷰 URL의 배너가 이미 존재합니다." }, { status: 400 }); }));
    mount(<BannerFormScreen />); fill(); fireEvent.click(screen.getByRole("button", { name: "배너 등록" })); fireEvent.click(screen.getByRole("button", { name: "등록하기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("동일한 웹뷰 URL");
    expect(posted).toHaveBeenCalledOnce(); expect(navigation.push).not.toHaveBeenCalled();
    expect(screen.getByRole("textbox", { name: "제목" })).toHaveValue("새 배너");
    expect(screen.getByRole("link", { name: "배너 목록을 확인해 주세요." })).toHaveAttribute("href", "/banners");
  });
  it("keeps deletion errors inside the confirmation dialog", async () => {
    server.use(http.delete("*/api/v1/admin/banners/:id", () => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "배너를 찾을 수 없습니다." }, { status: 404 })));
    mount(<BannerListScreen />); await screen.findByText(bannerFixtures[0].title);
    fireEvent.click(screen.getByRole("button", { name: `${bannerFixtures[0].title} 삭제` }));
    fireEvent.click(screen.getByRole("button", { name: "배너 삭제" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("배너를 찾을 수 없습니다.");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });
});
