import { Suspense } from "react";

import { FeedbackDetailScreen } from "@/features/feedbacks/ui/feedback-detail-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default async function FeedbackDetailPage({ params }: {
  params: Promise<{ feedbackId: string }>;
}) {
  const { feedbackId } = await params;
  return (
    <Suspense fallback={<LoadingSkeleton />}>
      <FeedbackDetailScreen key={feedbackId} feedbackId={feedbackId} />
    </Suspense>
  );
}
