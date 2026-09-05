import { Suspense } from "react";
import { UserDetailScreen } from "@/features/users/ui/user-detail-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default async function UserDetailPage(props: { params: Promise<{ userId: string }> }) {
  const { userId } = await props.params;
  return <Suspense fallback={<LoadingSkeleton rows={6} />}><UserDetailScreen userId={userId} /></Suspense>;
}
