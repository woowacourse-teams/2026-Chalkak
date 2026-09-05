package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import java.time.Instant;
import java.util.UUID;

public record AdminAuditLogQueryCriteria(
        UUID adminId,
        AdminAction action,
        AdminTargetType targetType,
        UUID targetId,
        Instant occurredFrom,
        Instant occurredTo,
        AdminAuditLogQuerySort sort
) {
}
