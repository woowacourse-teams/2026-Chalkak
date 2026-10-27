import type { AdminReminder, AdminReminderInput, AdminReminderRepeat, AdminReminderTarget } from "@/shared/api/contracts";
import { formatNoticeTime } from "@/features/notices/model/notice-display";

export const repeatLabels: Record<AdminReminderRepeat, string> = { ONCE: "일회성", DAILY: "매일 반복" };
export const targetLabels: Record<AdminReminderTarget, string> = {
  ALL: "전체 회원",
  TOPIC_PARTICIPANTS: "오늘 주제 참여자",
  TOPIC_NON_PARTICIPANTS: "오늘 주제 미참여자",
};

export function formatKstClock(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(new Date(value));
}

export function formatKstDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(value));
}

export function reminderSchedule(reminder: Pick<AdminReminder, "repeat" | "scheduledAt">) {
  if (reminder.repeat === "DAILY") return `매일 ${formatKstClock(reminder.scheduledAt)} KST · ${formatKstDate(reminder.scheduledAt)}부터`;
  return `${formatNoticeTime(reminder.scheduledAt)} KST`;
}

export function reminderStatus(reminder: AdminReminder, now = Date.now()) {
  const started = Date.parse(reminder.scheduledAt) <= now;
  if (reminder.repeat === "DAILY") return started ? "매일 발송 중" : "시작 전";
  return started ? "발송 시각 지남" : "발송 예정";
}

// The server rejects edits to a one-time reminder from its send time, so only deletion remains.
export function canEditReminder(reminder: AdminReminder, now = Date.now()) {
  return reminder.repeat === "DAILY" || now < Date.parse(reminder.scheduledAt);
}

// Like the server, check for a past time only when the schedule changes, so a started daily reminder's text stays editable.
export function isScheduleChanged(input: AdminReminderInput, original?: AdminReminder) {
  return !original || original.repeat !== input.repeat || Date.parse(original.scheduledAt) !== Date.parse(input.scheduledAt);
}

export function validateReminder(input: AdminReminderInput, original?: AdminReminder, now = Date.now()) {
  if (![...input.title.trim()].length || [...input.title.trim()].length > 50) return "제목은 앞뒤 공백을 제외하고 1~50자로 입력해 주세요.";
  if (![...input.body.trim()].length || [...input.body.trim()].length > 500) return "본문은 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.";
  if (!(input.repeat in repeatLabels)) return "반복 방식을 선택해 주세요.";
  if (!(input.target in targetLabels)) return "발송 대상을 선택해 주세요.";
  const scheduled = Date.parse(input.scheduledAt);
  if (!Number.isFinite(scheduled)) return "발송 시각을 한국 시간(KST)으로 입력해 주세요.";
  if (isScheduleChanged(input, original) && scheduled < now) return "발송 시각은 현재보다 과거일 수 없습니다. 지금 보내려면 다음 분처럼 가까운 미래 시각을 선택해 주세요.";
  return null;
}
