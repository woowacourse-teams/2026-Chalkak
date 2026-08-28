import type { PropsWithChildren } from "react";

/**
 * 실제 세션을 연결할 때 이 경계만 Guard 구현으로 교체합니다.
 * 현재 개발 단계에서는 관리자 식별자를 만들거나 브라우저에 저장하지 않습니다.
 */
export function AdminAccessBoundary({ children }: PropsWithChildren) {
  return children;
}
