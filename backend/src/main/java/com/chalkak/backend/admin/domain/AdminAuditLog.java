package com.chalkak.backend.admin.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "admin_audit_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAuditLog {

    private static final int MAX_REASON_LENGTH = 500;
    private static final String INVALID_AUDIT_LOG_MESSAGE =
            "관리자 감사 로그 정보가 올바르지 않습니다.";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "actor_admin_id", nullable = false, updatable = false)
    private UUID actorAdminId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "action",
            nullable = false,
            updatable = false,
            columnDefinition = "admin_action"
    )
    private AdminAction action;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "target_type",
            nullable = false,
            updatable = false,
            columnDefinition = "admin_target_type"
    )
    private AdminTargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private UUID targetId;

    @Column(name = "reason", length = MAX_REASON_LENGTH, updatable = false)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "before_state",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    @Getter(AccessLevel.NONE)
    private Map<String, Object> beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "after_state",
            nullable = false,
            updatable = false,
            columnDefinition = "jsonb"
    )
    @Getter(AccessLevel.NONE)
    private Map<String, Object> afterState;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    public static AdminAuditLog create(
            UUID actorAdminId,
            AdminAction action,
            AdminTargetType targetType,
            UUID targetId,
            String reason,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState,
            Instant occurredAt,
            UUID requestId
    ) {
        validateRequiredInformation(
                actorAdminId,
                action,
                targetType,
                targetId,
                beforeState,
                afterState,
                occurredAt,
                requestId
        );
        validateAction(actorAdminId, action, targetType, beforeState, afterState);
        String normalizedReason = normalizeReason(action, reason);

        AdminAuditLog auditLog = new AdminAuditLog();
        auditLog.actorAdminId = actorAdminId;
        auditLog.action = action;
        auditLog.targetType = targetType;
        auditLog.targetId = targetId;
        auditLog.reason = normalizedReason;
        auditLog.beforeState = beforeState.values();
        auditLog.afterState = afterState.values();
        auditLog.occurredAt = occurredAt;
        auditLog.requestId = requestId;
        return auditLog;
    }

    public Map<String, Object> getBeforeState() {
        return Collections.unmodifiableMap(beforeState);
    }

    public Map<String, Object> getAfterState() {
        return Collections.unmodifiableMap(afterState);
    }

    private static void validateRequiredInformation(
            UUID actorAdminId,
            AdminAction action,
            AdminTargetType targetType,
            UUID targetId,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState,
            Instant occurredAt,
            UUID requestId
    ) {
        if (actorAdminId == null
                || action == null
                || targetType == null
                || targetId == null
                || beforeState == null
                || afterState == null
                || occurredAt == null
                || requestId == null) {
            throw invalidAuditLogException();
        }
        if (beforeState.isEmpty() && afterState.isEmpty()) {
            throw invalidAuditLogException();
        }
    }

    private static void validateAction(
            UUID actorAdminId,
            AdminAction action,
            AdminTargetType targetType,
            AdminAuditSnapshot beforeState,
            AdminAuditSnapshot afterState
    ) {
        if (!action.isForTarget(targetType)
                || !action.isValidStateChange(actorAdminId, beforeState, afterState)) {
            throw invalidAuditLogException();
        }
    }

    private static String normalizeReason(AdminAction action, String reason) {
        if (reason == null || reason.isBlank()) {
            validateRequiredReason(action);
            return null;
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.codePointCount(0, normalizedReason.length()) > MAX_REASON_LENGTH) {
            throw invalidAuditLogException();
        }
        AdminAuditSensitiveValueValidator.validate(normalizedReason);
        return normalizedReason;
    }

    private static void validateRequiredReason(AdminAction action) {
        if (action.isReasonRequired()) {
            throw invalidAuditLogException();
        }
    }

    private static BusinessException invalidAuditLogException() {
        return new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_AUDIT_LOG_MESSAGE);
    }
}
