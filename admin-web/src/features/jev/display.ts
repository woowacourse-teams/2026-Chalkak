import type { Decision, Disposition, Role } from "./api";

export const questionLabels: Record<string, string> = {
  topic_fit: "주제 적합성",
  image_safety: "사진 안전성",
  image_expression: "사진 속 표현",
  topic_category: "주제 분류",
};

export const decisionLabels: Record<Decision, string> = {
  APPROVE: "승인 추천", HUMAN_REVIEW: "검토 추천", REJECT: "반려 추천",
};
export const goldLabels: Record<Decision, string> = {
  APPROVE: "승인", HUMAN_REVIEW: "검토 필요", REJECT: "반려",
};

export const roleLabels: Record<Role, string> = {
  JEV_TOPIC_CLASSIFICATION: "주제 분류",
  JEV_PHOTO_MODERATION: "사진 검수",
};
export const statusLabels: Record<string, string> = {
  APPROVED: "공개 중", REJECTED: "반려됨", PENDING: "관리자 검토 대기",
  VALIDATING: "이미지 처리 중", AUTO_APPROVED: "자동 승인", ADMIN_REVIEW: "관리자 검토",
  SKIPPED: "미반영", SENT: "전송 완료", FAILED: "최종 실패", DISABLED: "알림 꺼짐",
  SENDING: "전송 중", USER_DELETED: "작성자가 삭제함", SOURCE_DELETED: "검수 대상이 삭제됨",
  AUTOMATION_DISABLED: "자동 검수 꺼짐", CONFIGURATION_MISSING: "프롬프트 미설정",
  ADMIN_ALREADY_DECIDED: "이미 관리자가 판정함", DISPATCH_LIMIT_EXCEEDED: "작업 전달 한도 초과",
  CLASSIFICATION_FAILED: "주제 분류 실패", INVALID_RESPONSE: "검수 응답 오류",
};
export const slackLabel = (status: string | null) => status === "PENDING" ? "전송 대기" : status ? statusLabels[status] ?? status : "–";
export const displayDate = (value: string | null) => value ? new Intl.DateTimeFormat("ko-KR", {
  month: "short", day: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "Asia/Seoul",
}).format(new Date(value)) : "–";
export const probability = (value: number | null | undefined) => value == null ? "–" : new Intl.NumberFormat("ko-KR", { style: "percent", maximumFractionDigits: 1 }).format(value);
export const errorText = (error: unknown) => error instanceof Error ? error.message : "요청을 처리하지 못했어요.";

export function runStatus(preview: boolean, disposition: Disposition | null, finishedAt: string | null) {
  if (preview) return finishedAt ? "시험 완료" : "시험 진행 중";
  if (disposition) return statusLabels[disposition];
  return finishedAt ? "결과 반영 대기" : "처리 중";
}
