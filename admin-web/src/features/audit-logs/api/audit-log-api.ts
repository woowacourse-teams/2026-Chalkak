import { getApiClient } from "@/shared/api/client";
import type { AdminAuditLogListResponse } from "@/shared/api/contracts";
import { serializeQuery } from "@/shared/api/query-string";

export interface AdminAuditLogFilters {
  action?: string;
  targetType?: string;
  page: number;
}

export function fetchAdminAuditLogs(filters: AdminAuditLogFilters, signal?: AbortSignal) {
  return getApiClient().request<AdminAuditLogListResponse>("/audit-logs" + serializeQuery({
    ...filters,
    pageSize: 20,
    sort: "occurredAtDesc",
  }), { signal });
}
