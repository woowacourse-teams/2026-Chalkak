package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogSummaryProjection(
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
        UUID requestId
) {

    public AdminAuditLogSummaryProjection {
        beforeState = immutableCopy(beforeState);
        afterState = immutableCopy(afterState);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> state) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(state));
    }
}
