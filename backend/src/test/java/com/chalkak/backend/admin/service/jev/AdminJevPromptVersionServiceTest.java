package com.chalkak.backend.admin.service.jev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.aireview.AdminAiPromptVersionService;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService.CreateCommand;
import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.repository.AiPromptVersionRepository;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.jev.domain.JevPromptConfiguration;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminJevPromptVersionServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019ad963-213b-734e-a261-60e568c11056");

    @Autowired
    private AdminJevPromptVersionService service;
    @Autowired
    private JevPromptVersionRepository repository;
    @Autowired
    private AiPromptVersionRepository gptRepository;
    @Autowired
    private AdminAiPromptVersionService gptService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbc.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'jev-prompts-test', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @Test
    @DisplayName("같은 테이블에서 용도별 버전이 증가하고 JSONB 입력이 보존된다")
    void createVersion_perRole_incrementsAndPersistsConfiguration() {
        // When
        JevPromptVersion first = create(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        JevPromptVersion second = create(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        JevPromptVersion photo = create(JevPromptRole.JEV_PHOTO_MODERATION);
        entityManager.clear();

        // Then
        assertThat(first.getVersion()).isEqualTo(1);
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(photo.getVersion()).isEqualTo(1);
        assertThat(service.getVersion(photo.getId()).getConfiguration())
                .isEqualTo(JevPromptFixtures.photo());
        assertThat(repository.findActiveByRole(first.getRole())).isEmpty();
        assertThat(repository.findActiveByRole(photo.getRole())).isEmpty();
        assertThat(jdbc.queryForObject(
                "SELECT system_prompt IS NULL AND user_prompt_template IS NULL AND parameters IS NULL FROM ai_prompt_versions WHERE id = ?",
                Boolean.class,
                photo.getId()
        )).isTrue();
    }

    @Test
    @DisplayName("관리자가 입력하기 전 Jev 운영 프롬프트는 비어 있다")
    void getVersions_beforeManualInput_hasNoSeedOrActivePrompt() {
        // When & Then
        assertThat(service.getVersions(
                JevPromptRole.JEV_PHOTO_MODERATION,
                1,
                20
        ).items()).isEmpty();
        assertThat(repository.findActiveByRole(JevPromptRole.JEV_TOPIC_CLASSIFICATION)).isEmpty();
    }

    @Test
    @DisplayName("분류 선택지의 사진 규칙이 빠진 조합은 운영 적용하지 않는다")
    void updateActivation_missingCategoryRule_preservesPriorConfiguration() {
        // Given
        JevPromptVersion classification = create(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        service.updateActivation(
                ADMIN_ID,
                classification.getId(),
                true
        );
        JevPromptVersion photo = service.createVersion(ADMIN_ID, new CreateCommand(
                JevPromptRole.JEV_PHOTO_MODERATION,
                "jev-1.14.0",
                new JevPromptConfiguration(
                        JevPromptFixtures.photo().questions(),
                        Map.of("general", "기본 규칙"),
                        Map.of()
                ),
                null
        ));

        // When & Then
        assertThatThrownBy(() -> service.updateActivation(
                ADMIN_ID,
                photo.getId(),
                true
        )).isInstanceOf(BusinessException.class);
        assertThat(repository.findActiveByRole(JevPromptRole.JEV_PHOTO_MODERATION)).isEmpty();
    }

    @Test
    @DisplayName("새 버전 저장만으로 기존 활성 프롬프트가 바뀌지 않는다")
    void createVersion_whenApplied_preservesActiveVersion() {
        // Given
        JevPromptVersion first = create(JevPromptRole.JEV_PHOTO_MODERATION);
        service.updateActivation(
                ADMIN_ID,
                first.getId(),
                true
        );

        // When
        JevPromptVersion second = create(JevPromptRole.JEV_PHOTO_MODERATION);

        // Then
        assertThat(second.isActive()).isFalse();
        assertThat(repository.findActiveByRole(first.getRole()).orElseThrow().getId())
                .isEqualTo(first.getId());
    }

    @Test
    @DisplayName("명시적 적용과 비활성화는 해당 역할만 바꾸고 반복 요청의 감사를 늘리지 않는다")
    void updateActivation_applyReplaceAndDisable_isRoleScopedAndIdempotent() {
        // Given
        JevPromptVersion classification = create(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        JevPromptVersion first = create(JevPromptRole.JEV_PHOTO_MODERATION);
        JevPromptVersion second = create(JevPromptRole.JEV_PHOTO_MODERATION);
        service.updateActivation(
                ADMIN_ID,
                classification.getId(),
                true
        );
        service.updateActivation(
                ADMIN_ID,
                first.getId(),
                true
        );

        // When
        service.updateActivation(
                ADMIN_ID,
                second.getId(),
                true
        );
        service.updateActivation(
                ADMIN_ID,
                second.getId(),
                true
        );
        service.updateActivation(
                ADMIN_ID,
                first.getId(),
                false
        );

        // Then
        assertThat(repository.findActiveByRole(second.getRole()).orElseThrow().getId())
                .isEqualTo(second.getId());
        assertThat(repository.findActiveByRole(classification.getRole()).orElseThrow().getId())
                .isEqualTo(classification.getId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'JEV_PROMPT_ACTIVATION_UPDATED'",
                Long.class))
                .isEqualTo(3);
        service.updateActivation(
                ADMIN_ID,
                second.getId(),
                false
        );
        assertThat(repository.findActiveByRole(second.getRole())).isEmpty();
        assertThat(first.getConfiguration()).isEqualTo(second.getConfiguration());
    }

    @Test
    @DisplayName("GPT 경로는 Jev 버전을 읽지 않고 Jev 경로도 GPT 버전을 읽지 않는다")
    void getVersion_otherProvider_returnsNotFoundWithoutChangingGpt() {
        // Given
        JevPromptVersion jev = create(JevPromptRole.JEV_PHOTO_MODERATION);
        given(aiReviewGenerator.supportsModel("gpt-6.1-sol")).willReturn(true);
        var gpt = gptService.createVersion(ADMIN_ID, new AdminAiPromptVersionService.CreateCommand(
                AiPromptType.PHOTO_REVIEW,
                "GPT 시스템",
                "{{topicTitle}}",
                "gpt-6.1-sol",
                null,
                null,
                null,
                null,
                null
        ));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(gptRepository.findById(jev.getId())).isEmpty();
        assertThatThrownBy(() -> gptService.getVersion(jev.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(repository.findById(gpt.id())).isEmpty();
        assertThatThrownBy(() -> service.getVersion(gpt.id()))
                .isInstanceOf(NotFoundException.class);
        assertThat(gptRepository.findLatestByType(AiPromptType.PHOTO_REVIEW).orElseThrow().getId())
                .isEqualTo(gpt.id());
    }

    @Test
    @DisplayName("Jev 목록은 해당 역할의 최신순 페이지만 반환한다")
    void getVersions_pagination_returnsRequestedRoleOnly() {
        // Given
        create(JevPromptRole.JEV_PHOTO_MODERATION);
        JevPromptVersion latest = create(JevPromptRole.JEV_PHOTO_MODERATION);
        create(JevPromptRole.JEV_TOPIC_CLASSIFICATION);

        // When
        var page = service.getVersions(
                JevPromptRole.JEV_PHOTO_MODERATION,
                1,
                1
        );

        // Then
        assertThat(page.items()).extracting(JevPromptVersion::getId)
                .containsExactly(latest.getId());
        assertThat(page.hasNext()).isTrue();
    }

    private JevPromptVersion create(JevPromptRole role) {
        return service.createVersion(ADMIN_ID, new CreateCommand(
                role,
                "jev-1.14.0",
                role == JevPromptRole.JEV_TOPIC_CLASSIFICATION
                        ? JevPromptFixtures.classification()
                        : JevPromptFixtures.photo(),
                null
        ));
    }

    @ParameterizedTest
    @CsvSource({"0,20", "1,0", "1,101", "2147483647,100"})
    @DisplayName("페이지 범위와 offset 상한을 벗어난 목록 요청을 거절한다")
    void getVersions_invalidPagination_rejects(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> service.getVersions(
                JevPromptRole.JEV_PHOTO_MODERATION,
                page,
                pageSize
        )).isInstanceOf(BusinessException.class);
    }
}
