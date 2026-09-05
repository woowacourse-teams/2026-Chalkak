import { Suspense } from "react";
import { TopicDetailScreen } from "@/features/topics/ui/topic-detail-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";
export default async function TopicDetailPage(props:{params:Promise<{topicId:string}>}){const{topicId}=await props.params;return <Suspense fallback={<LoadingSkeleton rows={6}/>}><TopicDetailScreen topicId={topicId}/></Suspense>}
