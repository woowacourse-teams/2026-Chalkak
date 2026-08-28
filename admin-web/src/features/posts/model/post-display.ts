import type { AdminPostListItem, ModerationStatus } from "@/shared/api/contracts";
import type { ApiError } from "@/shared/api/errors";

export interface DisplayStatus {
  label: string;
  tone: "neutral" | "info" | "warning" | "success" | "danger";
}

const moderationDisplay: Record<ModerationStatus, DisplayStatus> = {
  VALIDATING: { label: "이미지 처리 중", tone: "info" },
  PENDING: { label: "검수 대기", tone: "warning" },
  APPROVED: { label: "승인", tone: "success" },
  REJECTED: { label: "거절", tone: "danger" },
};

export function getPostDisplayStatus(
  post: Pick<AdminPostListItem, "deletedAt" | "moderationStatus">,
): DisplayStatus {
  if (post.deletedAt) {
    return { label: "삭제", tone: "neutral" };
  }
  return moderationDisplay[post.moderationStatus];
}

export function formatInstant(
  value: string | null,
  timeZone = "Asia/Seoul",
) {
  if (!value) {
    return "—";
  }
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone,
  }).format(new Date(value));
}

export function formatFileSize(value?: number) {
  if (value === undefined) {
    return "—";
  }
  if (value < 1024) {
    return value + " B";
  }
  if (value < 1024 * 1024) {
    return (value / 1024).toFixed(1) + " KB";
  }
  return (value / (1024 * 1024)).toFixed(1) + " MB";
}

export function getPostErrorMessage(error: unknown) {
  const apiError = error as Partial<ApiError>;
  if (apiError.status === 404) {
    return "게시물이 없거나 더 이상 조회할 수 없습니다.";
  }
  if (apiError.errorCode === "RESOURCE_STATE_CHANGED") {
    return "다른 관리자가 이미 처리한 게시물입니다. 최신 상태를 다시 확인해 주세요.";
  }
  if (apiError.status === 403) {
    return "관리자 API 접근이 차단되었습니다. 개발 환경 설정을 확인해 주세요.";
  }
  if (apiError.kind === "network" || apiError.kind === "timeout") {
    return "서버에 연결하지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.";
  }
  if (typeof apiError.message === "string") {
    return apiError.message;
  }
  return "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.";
}
