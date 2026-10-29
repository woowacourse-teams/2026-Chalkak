import { QueryClientProvider } from "@tanstack/react-query";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { http, HttpResponse } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { server } from "@/mocks/server";
import { appVersionFixtures } from "@/mocks/app-version-handlers";
import type { AdminAppVersion, AdminAppVersionInput } from "@/shared/api/contracts";
import { ApiError } from "@/shared/api/errors";
import { createQueryClient, queryKeys } from "@/shared/query/query-client";
import { ToastProvider } from "@/shared/ui/toast";
import { saveAppVersion } from "../api/app-version-api";
import { AppVersionScreen } from "./app-version-screen";

beforeEach(() => {
  vi.stubEnv("NEXT_PUBLIC_API_MODE", "mock");
  vi.stubEnv("NEXT_PUBLIC_ADMIN_API_BASE_URL", "http://localhost:8080/api/v1/admin");
});
afterEach(() => vi.unstubAllEnvs());
const path = "*/api/v1/admin/app-versions";
const iosUrl = "https://apps.apple.com/app/id123456789";
function mount() {
  const client = createQueryClient();
  client.setDefaultOptions({ queries: { retry: false } });
  render(<QueryClientProvider client={client}><ToastProvider><AppVersionScreen /></ToastProvider></QueryClientProvider>);
  return client;
}
function card(label = "Android") { return within(screen.getByRole("region", { name: `${label} 앱 버전` })); }
async function edit(label = "Android") {
  const button = await card(label).findByRole("button", { name: label === "iOS" ? "설정 입력" : "수정" });
  fireEvent.click(button);
}
function fill(label = "Android", version = "1.10.0", reason = "스토어 출시 확인") {
  const scope = card(label);
  fireEvent.change(scope.getByRole("textbox", { name: "최소 지원 버전" }), { target: { value: version } });
  if (label === "iOS") fireEvent.change(scope.getByRole("textbox", { name: "스토어 주소" }), { target: { value: " " + iosUrl + " " } });
  fireEvent.change(scope.getByRole("textbox", { name: "변경 사유" }), { target: { value: reason } });
}
function confirm(label = "Android") {
  fireEvent.click(card(label).getByRole("button", { name: "저장 내용 확인" }));
  fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "설정 저장" }));
}

describe("app version screen", () => {
  it("distinguishes unconfigured settings, shows metadata, and links to audit history", async () => {
    mount();
    expect(screen.getByLabelText("콘텐츠 불러오는 중")).toBeInTheDocument();
    await screen.findByText("1.1.0");
    expect(card("iOS").getByText("업데이트 제한 없음")).toBeInTheDocument();
    expect(card().getByText(appVersionFixtures[0].lastChangedBy!)).toBeInTheDocument();
    expect(card().getByText("데모 설정")).toBeInTheDocument();
    expect(card().getByRole("link", { name: /스토어에서 확인/ })).toHaveAttribute("rel", "noopener noreferrer");
    expect(screen.getByRole("link", { name: "변경 이력 →" })).toHaveAttribute("href", "/audit-logs?targetType=APP_VERSION");
  });
  it("never treats failed or malformed queries as unconfigured and supports retry", async () => {
    server.use(http.get(path, () => HttpResponse.json({ errorCode: "NOT_FOUND", message: "아직 배포되지 않은 API입니다." }, { status: 404 })));
    mount();
    expect(await screen.findByRole("alert")).toHaveTextContent("아직 배포되지 않은 API");
    expect(screen.queryByText("업데이트 제한 없음")).not.toBeInTheDocument();
    server.use(http.get(path, () => HttpResponse.json({ appVersions: [] })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("응답이 올바르지"));
    server.use(http.get(path, () => HttpResponse.json({ appVersions: appVersionFixtures })));
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await screen.findByText("1.1.0");
  });
  it("confirms before first save, uses null revision, and invalidates audit history", async () => {
    const client = mount();
    client.setQueryData(queryKeys.auditLogs, {});
    await screen.findByText("1.1.0");
    await edit("iOS"); fill("iOS");
    fireEvent.click(card("iOS").getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("제한 없음 → 1.10.0");
    expect(screen.getByRole("dialog")).toHaveTextContent("실제 설치 가능한지 확인");
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "설정 저장" }));
    await screen.findByText("iOS 설정을 저장했습니다.");
    const saved = client.getQueryData<{ appVersions: AdminAppVersion[] }>(queryKeys.appVersions)!.appVersions[1];
    expect(saved).toMatchObject({ revision: 0, minimumSupportedVersion: "1.10.0", storeUrl: iosUrl, changeReason: "스토어 출시 확인" });
    expect(client.getQueryState(queryKeys.auditLogs)?.isInvalidated).toBe(true);
  });
  it("allows lowering the version and preserves another platform's unsaved form", async () => {
    mount(); await screen.findByText("1.1.0");
    await edit("iOS"); fill("iOS", "3.0.0", "iOS 작성 중");
    await edit(); fill("Android", "1.0.0"); confirm();
    await screen.findByText("Android 설정을 저장했습니다.");
    expect(card().getByText("1.0.0")).toBeInTheDocument();
    expect(card("iOS").getByRole("textbox", { name: "최소 지원 버전" })).toHaveValue("3.0.0");
    expect(card("iOS").getByRole("textbox", { name: "변경 사유" })).toHaveValue("iOS 작성 중");
  });
  it("requires a new reason, disables reason-only changes, and permits 500 emoji", async () => {
    mount(); await screen.findByText("1.1.0"); await edit();
    expect(card().getByRole("textbox", { name: "변경 사유" })).toHaveValue("");
    fireEvent.change(card().getByRole("textbox", { name: "변경 사유" }), { target: { value: "사유만 수정" } });
    expect(card().getByRole("button", { name: "저장 내용 확인" })).toBeDisabled();
    fill("Android", "1.10.0", "😀".repeat(501));
    fireEvent.click(card().getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("alert")).toHaveTextContent("1~500자");
    fill("Android", "1.10.0", "😀".repeat(500));
    expect(card().getByRole("textbox", { name: "변경 사유" })).not.toHaveAttribute("maxlength");
    confirm(); await screen.findByText("Android 설정을 저장했습니다.");
  });
  it("validates version and URL before opening the confirmation", async () => {
    mount(); await screen.findByText("1.1.0"); await edit(); fill("Android", "01.2.0");
    fireEvent.click(card().getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("alert")).toHaveTextContent("앞자리 0");
    fill();
    fireEvent.change(card().getByRole("textbox", { name: "스토어 주소" }), { target: { value: "javascript:alert(1)" } });
    fireEvent.click(card().getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("alert")).toHaveTextContent("공식 HTTPS");
    expect(card().queryByRole("link", { name: /스토어에서 확인/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
  it("keeps the editing baseline when the cache refreshes, then explicitly confirms the new revision", async () => {
    const sent = vi.fn();
    server.use(http.put(path + "/ANDROID", async ({ request }) => {
      const body = await request.json() as AdminAppVersionInput; sent(body);
      if (body.revision === 0) return HttpResponse.json({ errorCode: "RESOURCE_STATE_CHANGED", message: "수정 충돌" }, { status: 400 });
      return HttpResponse.json({ ...appVersionFixtures[0], ...body, revision: 2 });
    }));
    const client = mount(); await screen.findByText("1.1.0"); await edit(); fill();
    const changed = { ...appVersionFixtures[0], minimumSupportedVersion: "1.2.0", revision: 1 };
    act(() => client.setQueryData(queryKeys.appVersions, { appVersions: [changed, appVersionFixtures[1]] }));
    confirm();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("다른 관리자가"));
    expect(sent.mock.calls[0][0].revision).toBe(0);
    server.use(http.get(path, () => HttpResponse.json({ appVersions: [changed, appVersionFixtures[1]] })));
    fireEvent.click(card().getByRole("button", { name: "현재 설정 확인" }));
    fireEvent.click(await card().findByRole("button", { name: "최신 설정 기준으로 계속 작성" }));
    expect(card().getByRole("textbox", { name: "최소 지원 버전" })).toHaveValue("1.10.0");
    fireEvent.click(card().getByRole("button", { name: "저장 내용 확인" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("1.2.0 → 1.10.0");
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "설정 저장" }));
    await screen.findByText("Android 설정을 저장했습니다.");
    expect(sent.mock.calls[1][0].revision).toBe(1);
  });
  it("does not automatically resend an uncertain save and accepts a matching server result", async () => {
    let puts = 0;
    const saved = { ...appVersionFixtures[0], minimumSupportedVersion: "1.10.0", revision: 1 };
    server.use(http.put(path + "/ANDROID", () => { puts++; return HttpResponse.error(); }));
    mount(); await screen.findByText("1.1.0"); await edit(); fill(); confirm();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("이미 반영됐을 수"));
    expect(card().getByRole("button", { name: "저장 내용 확인" })).toBeDisabled();
    server.use(http.get(path, () => HttpResponse.json({ errorCode: "FAILED", message: "조회 실패" }, { status: 500 })));
    fireEvent.click(card().getByRole("button", { name: "현재 설정 확인" }));
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("현재 설정을 조회하지 못했습니다"));
    server.use(http.get(path, () => HttpResponse.json({ appVersions: [saved, appVersionFixtures[1]] })));
    fireEvent.click(card().getByRole("button", { name: "현재 설정 확인" }));
    fireEvent.click(await card().findByRole("button", { name: "저장된 설정 사용" }));
    expect(card().getByText("1.10.0")).toBeInTheDocument();
    expect(puts).toBe(1);
  });
  it("retries an uncertain save only after checking the unchanged server and confirming again", async () => {
    let puts = 0;
    server.use(http.put(path + "/ANDROID", async ({ request }) => {
      puts++;
      if (puts === 1) return HttpResponse.json({ errorCode: "ADMIN_API_TIMEOUT", message: "응답 시간 초과" }, { status: 504 });
      const body = await request.json() as AdminAppVersionInput;
      expect(body.revision).toBe(0);
      return HttpResponse.json({ ...appVersionFixtures[0], ...body, revision: 1 });
    }));
    mount(); await screen.findByText("1.1.0"); await edit(); fill(); confirm();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("이미 반영됐을 수"));
    fireEvent.click(card().getByRole("button", { name: "현재 설정 확인" }));
    fireEvent.click(await card().findByRole("button", { name: "최신 설정 기준으로 계속 작성" }));
    expect(puts).toBe(1);
    expect(card().getByRole("textbox", { name: "변경 사유" })).toHaveValue("스토어 출시 확인");
    confirm(); await screen.findByText("Android 설정을 저장했습니다.");
    expect(puts).toBe(2);
  });
  it("cancels editing without changing settings and clears the draft on reopening", async () => {
    mount(); await screen.findByText("1.1.0"); await edit(); fill();
    fireEvent.click(card().getByRole("button", { name: "취소" }));
    expect(card().getByText("1.1.0")).toBeInTheDocument();
    await edit();
    expect(card().getByRole("textbox", { name: "최소 지원 버전" })).toHaveValue("1.1.0");
    expect(card().getByRole("textbox", { name: "변경 사유" })).toHaveValue("");
  });
  it("blocks double clicks while a save is pending", async () => {
    let finish!: () => void; let puts = 0;
    const pending = new Promise<void>((resolve) => { finish = resolve; });
    server.use(http.put(path + "/ANDROID", async () => { puts++; await pending; return HttpResponse.json({ ...appVersionFixtures[0], minimumSupportedVersion: "1.10.0", revision: 1 }); }));
    mount(); await screen.findByText("1.1.0"); await edit(); fill();
    fireEvent.click(card().getByRole("button", { name: "저장 내용 확인" }));
    const button = within(screen.getByRole("dialog")).getByRole("button", { name: "설정 저장" });
    fireEvent.click(button); fireEvent.click(button);
    await waitFor(() => expect(puts).toBe(1));
    expect(within(screen.getByRole("dialog")).getByRole("button", { name: "처리 중…" })).toBeDisabled();
    finish(); await screen.findByText("Android 설정을 저장했습니다.");
  });
  it.each([401, 403])("preserves API authorization error %i without pretending the result is uncertain", async (status) => {
    server.use(http.put(path + "/ANDROID", () => HttpResponse.json({ errorCode: "FORBIDDEN", message: "관리자 인증을 확인해 주세요." }, { status })));
    mount(); await screen.findByText("1.1.0"); await edit(); fill(); confirm();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("관리자 인증"));
    expect(card().queryByRole("button", { name: "현재 설정 확인" })).not.toBeInTheDocument();
  });
  it("keeps a definite validation rejection editable", async () => {
    server.use(http.put(path + "/ANDROID", () => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message: "입력한 값을 확인해 주세요." }, { status: 400 })));
    mount(); await screen.findByText("1.1.0"); await edit(); fill(); confirm();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("입력한 값"));
    expect(card().getByRole("textbox", { name: "최소 지원 버전" })).toBeEnabled();
  });
  it("rejects an invalid successful save response as uncertain", async () => {
    server.use(http.put(path + "/ANDROID", () => HttpResponse.json(appVersionFixtures[1])));
    await expect(saveAppVersion("ANDROID", { revision: 0, minimumSupportedVersion: "1.10.0", storeUrl: appVersionFixtures[0].storeUrl!, reason: "확인" })).rejects.toBeInstanceOf(ApiError);
  });
});
