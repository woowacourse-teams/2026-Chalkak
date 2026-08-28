import { Suspense } from "react";

import { PostListScreen } from "@/features/posts/ui/post-list-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default function PostsPage() {
  return (
    <Suspense fallback={<LoadingSkeleton rows={5} />}>
      <PostListScreen />
    </Suspense>
  );
}
