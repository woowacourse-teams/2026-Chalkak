import { http, HttpResponse } from "msw";
import type { AdminNotice, AdminNoticeInput } from "@/shared/api/contracts";
import { validateNotice } from "@/features/notices/model/notice-display";

export const noticeFixtures: AdminNotice[] = [
  { noticeId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01", title: "서비스 점검 안내", body: "더 나은 서비스를 위해 점검을 진행합니다.\n점검 시간에는 일부 기능 이용이 제한될 수 있습니다.", scheduledAt: "2099-10-20T17:00:00Z", status: "SCHEDULED", distributedAt: null, deletedAt: null, createdAt: "2026-10-14T00:00:00Z", updatedAt: "2026-10-14T00:00:00Z" },
  { noticeId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02", title: "Chalkak의 새로운 소식을 전해요", body: "공지에서 서비스의 새로운 소식을 확인하세요.", scheduledAt: "2026-10-13T01:00:00Z", status: "DISTRIBUTED", distributedAt: "2026-10-13T01:00:01Z", deletedAt: null, createdAt: "2026-10-12T00:00:00Z", updatedAt: "2026-10-12T00:00:00Z" },
];
let notices = structuredClone(noticeFixtures);
export function resetNoticeMocks() { notices = structuredClone(noticeFixtures); }
const path = "*/api/v1/admin/notices";
const error = (message: string, status = 400) => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });
async function save(request: Request, id?: string) {
  const existing = id ? notices.find((notice) => notice.noticeId === id) : undefined;
  if (id && !existing) return error("공지를 찾을 수 없습니다.", 404);
  if (existing && (existing.deletedAt || existing.status === "DISTRIBUTED")) return error("배포되었거나 삭제된 공지는 수정할 수 없습니다.");
  const input = await request.json() as AdminNoticeInput;
  const message = validateNotice(input);
  if (message) return error(message);
  const now = new Date().toISOString();
  const notice: AdminNotice = { noticeId: id ?? crypto.randomUUID(), ...input, title: input.title.trim(), body: input.body.trim(), status: "SCHEDULED", distributedAt: null, deletedAt: null, createdAt: existing?.createdAt ?? now, updatedAt: now };
  notices = [notice, ...notices.filter((row) => row.noticeId !== notice.noticeId)];
  return HttpResponse.json(notice, { status: existing ? 200 : 201 });
}
export const noticeHandlers = [
  http.get(path, ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get("page") ?? 1);
    const sorted = [...notices].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    return HttpResponse.json({ currentPage: page, pageSize: 20, hasNext: page * 20 < sorted.length, notices: sorted.slice((page - 1) * 20, page * 20) });
  }),
  http.get(path + "/:id", ({ params }) => {
    const notice = notices.find((row) => row.noticeId === params.id);
    return notice ? HttpResponse.json(notice) : error("공지를 찾을 수 없습니다.", 404);
  }),
  http.post(path, ({ request }) => save(request)),
  http.put(path + "/:id", ({ request, params }) => save(request, String(params.id))),
  http.delete(path + "/:id", async ({ request, params }) => {
    const notice = notices.find((row) => row.noticeId === params.id);
    if (!notice) return error("공지를 찾을 수 없습니다.", 404);
    const { reason } = await request.json() as { reason?: string };
    if (!reason?.trim() || reason.trim().length > 500) return error("삭제 사유를 1~500자로 입력해 주세요.");
    notice.deletedAt ??= new Date().toISOString();
    return new HttpResponse(null, { status: 204 });
  }),
];
