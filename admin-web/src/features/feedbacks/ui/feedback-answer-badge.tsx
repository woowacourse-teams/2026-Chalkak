import type { AdminFeedbackAnswerState } from "@/shared/api/contracts";
import { StatusBadge } from "@/shared/ui/status-badge";

const answerLabels = {
  UNANSWERED: { label: "미답변", tone: "warning" },
  ANSWERED: { label: "답변 완료", tone: "success" },
  DELETED: { label: "답변 삭제됨", tone: "neutral" },
} as const;

export function FeedbackAnswerBadge({ answerState }: { answerState: AdminFeedbackAnswerState }) {
  const state = answerLabels[answerState];
  return state ? <StatusBadge tone={state.tone}>{state.label}</StatusBadge> : null;
}
