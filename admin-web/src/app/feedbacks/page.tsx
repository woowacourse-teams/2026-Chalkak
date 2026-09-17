import { Suspense } from "react";
import { FeedbackListScreen } from "@/features/feedbacks/ui/feedback-list-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default function FeedbacksPage() {
  return (
    <Suspense fallback={<LoadingSkeleton />}><FeedbackListScreen /></Suspense>
  );
}
