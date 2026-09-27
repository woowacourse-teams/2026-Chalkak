package com.chalkak.backend.admin.repository.audit;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.AdminPage;

public interface AdminAuditLogRepository {

    AdminAuditLog save(AdminAuditLog adminAuditLog);

    AdminPage<AdminAuditLogSummaryProjection> findAuditLogs(
            AdminAuditLogQueryCriteria criteria,
            int page,
            int pageSize
    );
}
