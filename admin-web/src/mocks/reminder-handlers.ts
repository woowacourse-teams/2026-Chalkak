import { http, HttpResponse } from "msw";
import type { AdminReminder, AdminReminderInput } from "@/shared/api/contracts";
import { canEditReminder, validateReminder } from "@/features/reminders/model/reminder-display";

export const reminderFixtures: AdminReminder[] = [
  { reminderId: "cccccccc-cccc-4ccc-8ccc-cccccccccc01", title: "오늘이 얼마 남지 않았어요", body: "사진을 자랑해 주세요!", repeat: "DAILY", target: "TOPIC_NON_PARTICIPANTS", scheduledAt: "2026-10-20T12:00:00Z", createdAt: "2026-10-19T09:00:00Z", updatedAt: "2026-10-19T09:00:00Z" },
  { reminderId: "cccccccc-cccc-4ccc-8ccc-cccccccccc02", title: "주말 특별 주제가 열렸어요", body: "이번 주말에는 특별한 주제로 사진을 남겨 보세요.", repeat: "ONCE", target: "ALL", scheduledAt: "2099-10-25T01:00:00Z", createdAt: "2026-10-18T09:00:00Z", updatedAt: "2026-10-18T09:00:00Z" },
  { reminderId: "cccccccc-cccc-4ccc-8ccc-cccccccccc03", title: "오늘도 참여해 주셔서 고마워요", body: "다른 회원의 사진도 구경해 보세요.", repeat: "ONCE", target: "TOPIC_PARTICIPANTS", scheduledAt: "2026-10-17T13:00:00Z", createdAt: "2026-10-17T09:00:00Z", updatedAt: "2026-10-17T09:00:00Z" },
];
let reminders = structuredClone(reminderFixtures);
const deleted = new Set<string>();
export function resetReminderMocks() { reminders = structuredClone(reminderFixtures); deleted.clear(); }
const path = "*/api/v1/admin/reminders";
const error = (message: string, status = 400) => HttpResponse.json({ errorCode: "BUSINESS_ERROR", message }, { status });
const auditState = (reminder: AdminReminder) => ({ title: reminder.title, repeat: reminder.repeat, target: reminder.target, scheduledAt: reminder.scheduledAt });

export function createReminderHandlers(record: (action: string, id: string, before: Record<string, unknown>, after: Record<string, unknown>) => void) {
  async function save(request: Request, id?: string) {
    const existing = id ? reminders.find((reminder) => reminder.reminderId === id) : undefined;
    if (id && deleted.has(id)) return error("삭제된 리마인더는 수정할 수 없습니다.");
    if (id && !existing) return error("리마인더를 찾을 수 없습니다.", 404);
    if (existing && !canEditReminder(existing)) return error("발송 시각이 지난 일회성 리마인더는 수정할 수 없습니다.");
    const input = await request.json() as AdminReminderInput;
    const message = validateReminder(input, existing);
    if (message) return error(message);
    const now = new Date().toISOString();
    const reminder: AdminReminder = { reminderId: id ?? crypto.randomUUID(), ...input, title: input.title.trim(), body: input.body.trim(), createdAt: existing?.createdAt ?? now, updatedAt: now };
    reminders = [reminder, ...reminders.filter((row) => row.reminderId !== reminder.reminderId)];
    record(existing ? "REMINDER_UPDATED" : "REMINDER_CREATED", reminder.reminderId, existing ? auditState(existing) : {}, auditState(reminder));
    return HttpResponse.json(reminder, { status: existing ? 200 : 201 });
  }
  return [
    http.get(path, ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get("page") ?? 1);
      const sorted = [...reminders].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.reminderId.localeCompare(a.reminderId));
      return HttpResponse.json({ currentPage: page, pageSize: 20, hasNext: page * 20 < sorted.length, reminders: sorted.slice((page - 1) * 20, page * 20) });
    }),
    http.post(path, ({ request }) => save(request)),
    http.put(path + "/:id", ({ request, params }) => save(request, String(params.id))),
    http.delete(path + "/:id", ({ params }) => {
      // Like the server, deleting an already deleted reminder keeps the first result.
      if (deleted.has(String(params.id))) return new HttpResponse(null, { status: 204 });
      const reminder = reminders.find((row) => row.reminderId === params.id);
      if (!reminder) return error("리마인더를 찾을 수 없습니다.", 404);
      reminders = reminders.filter((row) => row.reminderId !== reminder.reminderId);
      deleted.add(reminder.reminderId);
      record("REMINDER_DELETED", reminder.reminderId, auditState(reminder), { ...auditState(reminder), deletedAt: new Date().toISOString() });
      return new HttpResponse(null, { status: 204 });
    }),
  ];
}
