package com.chalkak.backend.admin.infrastructure.persistence.audit;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.audit.AdminAuditLogSummaryProjection;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

interface AdminAuditLogJpaRepository extends Repository<AdminAuditLog, UUID> {

    <S extends AdminAuditLog> S save(S adminAuditLog);

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.audit.AdminAuditLogSummaryProjection(
                auditLog.id,
                auditLog.actorAdminId,
                admin.username,
                auditLog.action,
                auditLog.targetType,
                auditLog.targetId,
                auditLog.reason,
                auditLog.beforeState,
                auditLog.afterState,
                auditLog.occurredAt,
                auditLog.requestId
            )
            FROM AdminAuditLog auditLog
            JOIN Admin admin ON admin.id = auditLog.actorAdminId
            WHERE (:#{#criteria.adminId() == null} = true OR auditLog.actorAdminId = :#{#criteria.adminId()})
              AND (:#{#criteria.action() == null} = true OR auditLog.action = :#{#criteria.action()})
              AND (:#{#criteria.targetType() == null} = true OR auditLog.targetType = :#{#criteria.targetType()})
              AND (:#{#criteria.targetId() == null} = true OR auditLog.targetId = :#{#criteria.targetId()})
              AND (:#{#criteria.occurredFrom() == null} = true OR auditLog.occurredAt >= :#{#criteria.occurredFrom()})
              AND (:#{#criteria.occurredTo() == null} = true OR auditLog.occurredAt <= :#{#criteria.occurredTo()})
            """)
    Slice<AdminAuditLogSummaryProjection> findAuditLogs(
            @Param("criteria") AdminAuditLogQueryCriteria criteria,
            Pageable pageable
    );
}
