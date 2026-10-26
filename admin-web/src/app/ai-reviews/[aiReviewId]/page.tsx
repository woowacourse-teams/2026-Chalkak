import { AiReviewDetailScreen } from "@/features/ai-reviews/ui/ai-review-detail-screen";

export default async function AiReviewDetailPage({ params }: { params: Promise<{ aiReviewId: string }> }) {
  const { aiReviewId } = await params;
  return <AiReviewDetailScreen aiReviewId={aiReviewId} />;
}
