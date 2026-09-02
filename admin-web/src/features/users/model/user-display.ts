import type { UserStatus } from "@/shared/api/contracts";
import type { ApiError } from "@/shared/api/errors";

export const userStatusDisplay: Record<
  UserStatus,
  { label: string; tone: "success" | "danger" | "neutral" }
> = {
  ACTIVE: { label: "활성", tone: "success" },
  BANNED: { label: "차단", tone: "danger" },
  WITHDRAWN: { label: "탈퇴", tone: "neutral" },
};

export function getUserErrorMessage(error: unknown) {
  const value = error as Partial<ApiError>;
  if (value.status === 404) return "사용자를 찾을 수 없습니다.";
  if (value.status === 403) return "관리자 API에 접근할 수 없습니다.";
  if (value.errorCode === "RESOURCE_STATE_CHANGED")
    return "다른 관리자가 이미 상태를 변경했습니다. 최신 상태를 확인해 주세요.";
  return value.message || "사용자 요청을 처리하지 못했습니다.";
}
