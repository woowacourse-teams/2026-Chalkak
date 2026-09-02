import { Suspense } from "react";
import { AuditLogScreen } from "@/features/audit-logs/ui/audit-log-screen";
import { LoadingSkeleton } from "@/shared/ui/feedback-states";

export default function AuditLogsPage() {
  return (
    <Suspense fallback={<LoadingSkeleton />}><AuditLogScreen /></Suspense>
  );
}
