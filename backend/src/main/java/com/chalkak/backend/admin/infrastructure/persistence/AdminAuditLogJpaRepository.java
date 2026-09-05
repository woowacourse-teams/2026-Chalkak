package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface AdminAuditLogJpaRepository extends Repository<AdminAuditLog, UUID> {

    <S extends AdminAuditLog> S save(S adminAuditLog);
}
