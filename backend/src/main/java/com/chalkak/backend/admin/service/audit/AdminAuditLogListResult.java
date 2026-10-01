package com.chalkak.backend.admin.service.audit;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSummaryProjection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AuditLogSummary> auditLogs) {

    public AdminAuditLogListResult {
        auditLogs = List.copyOf(auditLogs);
    }

    public static AdminAuditLogListResult from(AdminPage<AdminAuditLogSummaryProjection> page) {
        return new AdminAuditLogListResult(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.items().stream().map(AuditLogSummary::from).toList());
    }

    public record AuditLogSummary(
            UUID auditLogId,
            UUID actorAdminId,
            String actorUsername,
            AdminAction action,
            AdminTargetType targetType,
            UUID targetId,
            String reason,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Instant occurredAt,
            UUID requestId) {

        private static AuditLogSummary from(AdminAuditLogSummaryProjection projection) {
            return new AuditLogSummary(
                    projection.auditLogId(), projection.actorAdminId(),
                    projection.actorUsername(), projection.action(), projection.targetType(),
                    projection.targetId(), projection.reason(), projection.beforeState(),
                    projection.afterState(), projection.occurredAt(), projection.requestId());
        }
    }
}
