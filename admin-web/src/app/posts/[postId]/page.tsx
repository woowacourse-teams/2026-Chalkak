import { Suspense } from "react";

import { PostDetailScreen } from "@/features/posts/ui/post-detail-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default async function PostDetailPage(
  props: { params: Promise<{ postId: string }> },
) {
  const { postId } = await props.params;

  return (
    <Suspense fallback={<LoadingSkeleton rows={6} />}>
      <PostDetailScreen postId={postId} />
    </Suspense>
  );
}
