import type { AdminNotice, AdminNoticeInput } from "@/shared/api/contracts";

export function noticeStatus(notice: AdminNotice) {
  if (notice.deletedAt) return notice.status === "SCHEDULED" ? "발송 취소" : "배포 후 숨김";
  return notice.status === "SCHEDULED" ? "예약 대기" : "알림함 배포 완료";
}

export function canEditNotice(notice: AdminNotice) {
  return notice.status === "SCHEDULED" && !notice.deletedAt;
}

export function formatNoticeTime(value: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23",
  }).format(new Date(value));
}

export function toKstInput(value: string) {
  return new Date(new Date(value).getTime() + 9 * 60 * 60 * 1000).toISOString().slice(0, 19);
}

export function fromKstInput(value: string) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) return null;
  const date = new Date(`${value}+09:00`);
  if (!Number.isFinite(date.getTime()) || toKstInput(date.toISOString()) !== (value.length === 16 ? `${value}:00` : value)) return null;
  return date.toISOString();
}

export function validateNotice(input: AdminNoticeInput, now = Date.now()) {
  if (![...input.title.trim()].length || [...input.title.trim()].length > 50) return "제목은 앞뒤 공백을 제외하고 1~50자로 입력해 주세요.";
  if (![...input.body.trim()].length || [...input.body.trim()].length > 500) return "본문은 앞뒤 공백을 제외하고 1~500자로 입력해 주세요.";
  const scheduled = Date.parse(input.scheduledAt);
  if (!Number.isFinite(scheduled)) return "예약 시각을 한국 시간(KST)으로 입력해 주세요.";
  if (scheduled < now - 5 * 60 * 1000) return "예약 시각은 현재보다 5분 넘게 과거일 수 없습니다. 시간을 다시 선택해 주세요.";
  return null;
}

export function noticeEnvironment() {
  if (process.env.NEXT_PUBLIC_API_MODE === "mock") return "데모 환경";
  try {
    const host = new URL(process.env.NEXT_PUBLIC_ADMIN_API_BASE_URL ?? "").hostname;
    return host === "chalkak-dev.pysun.kr" ? "개발 서버" : `연결 서버 · ${host}`;
  } catch { return "연결 서버 미설정"; }
}
