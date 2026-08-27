package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.AdminAuditLog;

public interface AdminAuditLogRepository {

    AdminAuditLog save(AdminAuditLog adminAuditLog);
}
