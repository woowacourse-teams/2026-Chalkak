package com.chalkak.backend.admin.repository;

import java.util.List;

public record AdminAuditLogQueryPage(
        List<AdminAuditLogSummaryProjection> auditLogs,
        int currentPage,
        int pageSize,
        boolean hasNext
) {

    public AdminAuditLogQueryPage {
        auditLogs = List.copyOf(auditLogs);
    }
}
