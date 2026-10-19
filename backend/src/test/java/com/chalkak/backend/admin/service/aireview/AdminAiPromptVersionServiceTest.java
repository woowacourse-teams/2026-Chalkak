package com.chalkak.backend.admin.service.aireview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.CreateCommand;
import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService.VersionResult;
import com.chalkak.backend.aireview.domain.AiPromptParameters;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ImageDetail;
import com.chalkak.backend.aireview.domain.AiPromptParameters.ReasoningEffort;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import com.chalkak.backend.aireview.domain.PhotoReviewOutput;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminAiPromptVersionServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000688");
    private static final String MODEL = "gpt-6.1-sol";

    @Autowired
    private AdminAiPromptVersionService versionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MDC.put(LogFields.REQUEST_ID, requestId.toString());
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'prompt-version-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        given(aiReviewGenerator.supportsModel(MODEL)).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("첫 버전은 1이고 이어서 만들면 2, 3으로 올라가며 이전 버전은 바뀌지 않는다")
    void createVersion_sequential_incrementsVersionPerType() {
        // When
        VersionResult first = versionService.createVersion(ADMIN_ID, command("첫 시스템", null));
        VersionResult second = versionService.createVersion(ADMIN_ID, command("둘째 시스템", null));
        VersionResult third = versionService.createVersion(ADMIN_ID, command("셋째 시스템", null));
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(third.version()).isEqualTo(3);
        assertThat(entityManager.find(AiPromptVersion.class, first.id()).getSystemPrompt())
                .isEqualTo("첫 시스템");
    }

    @Test
    @DisplayName("저장한 버전은 파라미터를 JSONB로 왕복하고 DB 행에 값이 그대로 남는다")
    void createVersion_validInput_persistsAllColumns() {
        // When
        VersionResult result = versionService.createVersion(ADMIN_ID, new CreateCommand(
                AiPromptType.PHOTO_REVIEW, "시스템", "주제는 {{topicTitle}}", MODEL,
                ImageDetail.LOW, 700, ReasoningEffort.HIGH, 6.5, "첫 실험"));
        entityManager.flush();
        entityManager.clear();

        // Then
        AiPromptVersion stored = entityManager.find(AiPromptVersion.class, result.id());
        assertThat(stored.getParameters())
                .isEqualTo(new AiPromptParameters(ImageDetail.LOW, 700, ReasoningEffort.HIGH, 6.5));
        assertThat(stored.getUserPromptTemplate()).isEqualTo("주제는 {{topicTitle}}");
        assertThat(stored.getCreatedAt()).isEqualTo(result.createdAt());
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.id().version()).isEqualTo(7);
        assertThat(result.outputSchemaVersion())
                .isEqualTo(String.valueOf(PhotoReviewOutput.schemaVersion()));
        assertThat(result.createdByAdminId()).isEqualTo(ADMIN_ID);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT type, version, model, memo, output_schema_version, created_by_admin_id "
                        + "FROM ai_prompt_versions WHERE id = ?", result.id()))
                .containsEntry("type", "PHOTO_REVIEW")
                .containsEntry("version", 1)
                .containsEntry("model", MODEL)
                .containsEntry("memo", "첫 실험")
                .containsEntry("created_by_admin_id", ADMIN_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT parameters ->> 'distinctivePerspectiveThreshold' FROM ai_prompt_versions "
                        + "WHERE id = ?", String.class, result.id())).isEqualTo("6.5");
    }

    @Test
    @DisplayName("옵션을 생략하면 기본값으로 채워 저장한다")
    void createVersion_defaultParameters_storesDefaults() {
        // When
        VersionResult result = versionService.createVersion(ADMIN_ID, command("시스템", null));
        entityManager.flush();
        entityManager.clear();

        // Then
        AiPromptParameters stored =
                entityManager.find(AiPromptVersion.class, result.id()).getParameters();
        assertThat(stored.imageDetail()).isEqualTo(ImageDetail.HIGH);
        assertThat(stored.maxOutputTokens()).isEqualTo(8000);
        assertThat(stored.reasoningEffort()).isNull();
        assertThat(stored.distinctivePerspectiveThreshold()).isEqualTo(7.0);
        assertThat(result.parameters()).isEqualTo(stored);
    }

    @Test
    @DisplayName("허용되지 않은 모델이면 400이고 아무것도 저장하지 않는다")
    void createVersion_unsupportedModel_throwsWithoutSaving() {
        // Given
        given(aiReviewGenerator.supportsModel("unknown-model")).willReturn(false);
        CreateCommand command = new CreateCommand(AiPromptType.PHOTO_REVIEW, "시스템",
                "{{topicTitle}}", "unknown-model", null, null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> versionService.createVersion(ADMIN_ID, command))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
                    assertThat(exception.getMessage()).isEqualTo("허용되지 않은 모델입니다.");
                });
        assertThat(countVersions()).isZero();
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("허용되지 않은 템플릿 변수면 400이고 아무것도 저장하지 않는다")
    void createVersion_invalidTemplateVariable_throwsWithoutSaving() {
        // Given
        CreateCommand command = new CreateCommand(AiPromptType.PHOTO_REVIEW, "시스템",
                "{{userId}}", MODEL, null, null, null, null, null);

        // When & Then
        assertThatThrownBy(() -> versionService.createVersion(ADMIN_ID, command))
                .isInstanceOf(BusinessException.class);
        assertThat(countVersions()).isZero();
        assertThat(countAudits()).isZero();
    }

    @Test
    @DisplayName("버전 생성은 프롬프트 원문 없이 유형과 버전만 감사 로그에 남긴다")
    void createVersion_validInput_recordsAuditWithoutPromptText() {
        // When
        VersionResult result = versionService.createVersion(ADMIN_ID, command("비밀 시스템", "메모"));
        entityManager.flush();
        entityManager.clear();

        // Then
        AdminAuditLog audit = entityManager.createQuery(
                "SELECT a FROM AdminAuditLog a WHERE a.targetId = :id", AdminAuditLog.class)
                .setParameter("id", result.id()).getSingleResult();
        assertThat(audit.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(audit.getAction().name()).isEqualTo("AI_PROMPT_VERSION_CREATED");
        assertThat(audit.getTargetType().name()).isEqualTo("AI_PROMPT_VERSION");
        assertThat(audit.getRequestId()).isEqualTo(requestId);
        assertThat(audit.getReason()).isNull();
        assertThat(audit.getBeforeState()).isEmpty();
        assertThat(audit.getAfterState()).hasSize(2)
                .containsEntry("type", "PHOTO_REVIEW")
                .containsEntry("version", 1);
    }

    private CreateCommand command(String systemPrompt, String memo) {
        return new CreateCommand(AiPromptType.PHOTO_REVIEW, systemPrompt, "{{topicTitle}}", MODEL,
                null, null, null, null, memo);
    }

    private int countVersions() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_prompt_versions WHERE created_by_admin_id = ?",
                Integer.class, ADMIN_ID);
    }

    private int countAudits() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE actor_admin_id = ?",
                Integer.class, ADMIN_ID);
    }
}
