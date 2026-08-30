package com.chalkak.backend.admin.repository;

public interface AdminAuditLogQueryRepository {

    AdminAuditLogQueryPage findAuditLogs(
            AdminAuditLogQueryCriteria criteria,
            int page,
            int pageSize
    );
}
