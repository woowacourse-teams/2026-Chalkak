package com.chalkak.backend.aireview.domain;

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
import java.util.function.Predicate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 관리자가 만든 AI 프롬프트 버전. 만든 뒤에는 바꾸지 않으며, 같은 용도에서 가장 큰 버전이 최신이다.
 */
@Entity
@Table(name = "ai_prompt_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiPromptVersion {

    private static final int MAX_MODEL_LENGTH = 100;
    private static final int MAX_OUTPUT_SCHEMA_VERSION_LENGTH = 50;
    private static final int MAX_MEMO_LENGTH = 500;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 30)
    private AiPromptType type;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "system_prompt", nullable = false, updatable = false, columnDefinition = "text")
    private String systemPrompt;

    @Column(name = "user_prompt_template", nullable = false, updatable = false, columnDefinition = "text")
    private String userPromptTemplate;

    @Column(nullable = false, updatable = false, length = MAX_MODEL_LENGTH)
    private String model;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
    private AiPromptParameters parameters;

    @Column(name = "output_schema_version", nullable = false, updatable = false, length = MAX_OUTPUT_SCHEMA_VERSION_LENGTH)
    private String outputSchemaVersion;

    @Column(updatable = false, length = MAX_MEMO_LENGTH)
    private String memo;

    @Column(name = "created_by_admin_id", nullable = false, updatable = false)
    private UUID createdByAdminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static AiPromptVersion create(
            AiPromptDraft draft,
            int version,
            String memo,
            UUID createdByAdminId
    ) {
        if (draft == null || version < 1 || createdByAdminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "프롬프트 버전 정보가 올바르지 않습니다.");
        }
        AiPromptVersion promptVersion = new AiPromptVersion();
        promptVersion.type = draft.type();
        promptVersion.version = version;
        promptVersion.systemPrompt = draft.systemPrompt();
        promptVersion.userPromptTemplate = draft.userPromptTemplate();
        promptVersion.model = draft.model();
        promptVersion.parameters = draft.parameters();
        promptVersion.outputSchemaVersion = draft.type().outputSchemaVersion();
        promptVersion.memo = normalizeMemo(memo);
        promptVersion.createdByAdminId = createdByAdminId;
        return promptVersion;
    }

    /**
     * 이 버전이 모델에게 요구하는 출력 스키마가 서버가 지금 해석할 수 있는 버전인지 확인한다.
     */
    public boolean hasCurrentOutputSchema() {
        return outputSchemaVersion.equals(type.outputSchemaVersion());
    }

    /**
     * 이 버전을 지금 서버가 호출할 수 있는지 확인한다. 서버가 해석하는 출력 스키마이고 허용된 모델이어야 한다.
     */
    public boolean isRunnableWith(Predicate<String> modelSupported) {
        return hasCurrentOutputSchema() && modelSupported.test(model);
    }

    public AiPromptDraft toDraft() {
        return new AiPromptDraft(
                type,
                systemPrompt,
                userPromptTemplate,
                model,
                parameters
        );
    }

    private static String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        String normalized = memo.strip();
        if (normalized.codePointCount(0, normalized.length()) > MAX_MEMO_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "메모는 500자 이하여야 합니다.");
        }
        return normalized;
    }
}
