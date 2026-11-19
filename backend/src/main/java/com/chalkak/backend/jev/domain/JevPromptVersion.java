package com.chalkak.backend.jev.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

/** 같은 저장 테이블의 Jev 행만 매핑한다. GPT 필드와 모델 옵션은 Jev 경로에 노출하지 않는다. */
@Entity
@Table(name = "ai_prompt_versions")
@SQLRestriction("type IN ('JEV_TOPIC_CLASSIFICATION', 'JEV_PHOTO_MODERATION')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JevPromptVersion {

    private static final String MODEL = "jev-1.14.0";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private JevPromptRole role;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(nullable = false, updatable = false, length = 100)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "jev_configuration", updatable = false, columnDefinition = "jsonb")
    private JevPromptConfiguration configuration;

    @Column(name = "output_schema_version", nullable = false, updatable = false, length = 50)
    private String outputSchemaVersion;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(updatable = false, length = 500)
    private String memo;

    @Column(name = "created_by_admin_id", nullable = false, updatable = false)
    private UUID createdByAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static JevPromptVersion create(
            JevPromptRole role,
            String model,
            JevPromptConfiguration configuration,
            int version,
            String memo,
            UUID adminId
    ) {
        if (role == null || configuration == null || version < 1 || adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "Jev 프롬프트 버전 정보가 올바르지 않습니다.");
        }
        if (!MODEL.equals(model)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "검증된 Jev 모델 jev-1.14.0을 지정해 주세요.");
        }
        configuration.validateRole(role);
        String normalizedMemo = memo == null || memo.isBlank() ? null : memo.strip();
        if (normalizedMemo != null
                && normalizedMemo.codePointCount(0, normalizedMemo.length()) > 500) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "메모는 500자 이하여야 합니다.");
        }
        JevPromptVersion prompt = new JevPromptVersion();
        prompt.role = role;
        prompt.version = version;
        prompt.model = model;
        prompt.configuration = configuration;
        prompt.outputSchemaVersion = "jev-choice-v1";
        prompt.memo = normalizedMemo;
        prompt.createdByAdminId = adminId;
        return prompt;
    }

    public void activate(Instant now) {
        active = true;
        activatedAt = now;
    }

    public void deactivate() {
        active = false;
    }
}
