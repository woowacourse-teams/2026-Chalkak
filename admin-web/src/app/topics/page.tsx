import { Suspense } from "react";
import { TopicListScreen } from "@/features/topics/ui/topic-list-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default function TopicsPage() {
  return <Suspense fallback={<LoadingSkeleton rows={5}/>}><TopicListScreen/></Suspense>;
}
