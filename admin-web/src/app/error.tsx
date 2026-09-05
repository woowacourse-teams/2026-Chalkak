"use client";

import { ErrorState } from "@/shared/ui/feedback-states";

export default function ErrorPage({
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <ErrorState
      description="잠시 후 다시 시도해 주세요. 문제가 계속되면 개발 환경 설정을 확인해 주세요."
      onRetry={reset}
      title="관리자 화면에 문제가 발생했습니다"
    />
  );
}
