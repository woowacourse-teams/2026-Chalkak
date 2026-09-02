package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import java.util.UUID;

public record AdminAuditLogCommand(
        UUID actorAdminId,
        AdminAction action,
        AdminTargetType targetType,
        UUID targetId,
        String reason,
        AdminAuditSnapshot beforeState,
        AdminAuditSnapshot afterState,
        UUID requestId
) {
}
