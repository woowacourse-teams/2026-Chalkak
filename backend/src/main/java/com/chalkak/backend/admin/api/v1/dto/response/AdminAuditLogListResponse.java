package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.AdminAuditLogListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminAuditLogListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AuditLogResponse> auditLogs
) {

    public static AdminAuditLogListResponse from(AdminAuditLogListResult result) {
        return new AdminAuditLogListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.auditLogs().stream().map(AuditLogResponse::from).toList()
        );
    }

    public record AuditLogResponse(
            UUID auditLogId,
            UUID actorAdminId,
            String actorUsername,
            AdminAction action,
            AdminTargetType targetType,
            UUID targetId,
            String reason,
            @Schema(description = "작업별 허용 필드로 구성된 변경 전 상태. 민감정보는 포함하지 않습니다.")
            Map<String, Object> beforeState,
            @Schema(description = "작업별 허용 필드로 구성된 변경 후 상태. 민감정보는 포함하지 않습니다.")
            Map<String, Object> afterState,
            Instant occurredAt,
            UUID requestId
    ) {

        private static AuditLogResponse from(AdminAuditLogListResult.AuditLogSummary result) {
            return new AuditLogResponse(
                    result.auditLogId(), result.actorAdminId(), result.actorUsername(),
                    result.action(), result.targetType(), result.targetId(), result.reason(),
                    result.beforeState(), result.afterState(), result.occurredAt(), result.requestId()
            );
        }
    }
}
