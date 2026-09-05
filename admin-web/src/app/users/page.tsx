import { Suspense } from "react";
import { UserListScreen } from "@/features/users/ui/user-list-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default function UsersPage() {
  return <Suspense fallback={<LoadingSkeleton rows={5} />}><UserListScreen /></Suspense>;
}
