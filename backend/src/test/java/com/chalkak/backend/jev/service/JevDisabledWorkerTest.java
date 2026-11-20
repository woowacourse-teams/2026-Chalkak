package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPromptDraft;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import com.chalkak.backend.jev.infrastructure.infra.JevJobDispatcher;
import com.chalkak.backend.jev.infrastructure.infra.JevResultApplicationScheduler;
import com.chalkak.backend.jev.infrastructure.infra.JevSlackDispatcher;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository;
import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(properties = "chalkak.jev.worker-enabled=false")
class JevDisabledWorkerTest extends IntegrationTestSupport {

    private static final UUID ADMIN = UUID.fromString("019ad963-213b-734e-a261-60e568c15056");
    private static final UUID TOPIC = UUID.fromString("019ad963-213b-734e-a261-60e568c15058");
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Autowired
    private ApplicationContext context;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JevJobRepository jobs;
    @Autowired
    private JevPromptVersionRepository versions;
    @Autowired
    private PostRepository posts;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private UserRepository users;
    @Autowired
    private JevResponseParser parser;
    @Autowired
    private JevClassificationKeyFactory keys;
    @Autowired
    private JevJobService execution;
    @Autowired
    private JevResultApplicationService application;
    @Autowired
    private JevResultApplicationScheduler scheduler;
    @Autowired
    private JevSlackDispatcher slack;
    @Autowired
    private NotificationService notifications;
    @Autowired
    private AdminJevPromptVersionService prompts;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private ObjectMapper mapper;
    @MockitoBean
    private Clock clock;

    private JevPromptVersion classification;
    private JevPromptVersion photo;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        cleanOwnRows();
        given(clock.instant()).willReturn(NOW);
        jdbc.update("INSERT INTO admins(id, username, password) VALUES (?, 'jev-off-test', 'test')",
                ADMIN);
        jdbc.update(
                """
                        INSERT INTO topics(id, title, topic_date, starts_at, ends_at)
                        VALUES (?, '초록', '2026-11-20', ?, ?)
                        """,
                TOPIC,
                Timestamp.from(NOW.minusSeconds(1)),
                Timestamp.from(NOW.plusSeconds(3600))
        );
        classification = createPrompt(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        photo = createPrompt(JevPromptRole.JEV_PHOTO_MODERATION);
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbc).clean();
        cleanOwnRows();
    }

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "RETRY_WAIT", "IN_FLIGHT"})
    @DisplayName("워커가 꺼진 실제 구성에서 미완료 작업을 닫고 관리자 Slack을 한 번만 예약한다")
    void apply_workerDisabled_closesUnfinishedAndNotifiesOnce(String stage) {
        // Given
        UUID id = createRun("PENDING");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var run = jobs.findRunForUpdate(id).orElseThrow();
            if (stage.equals("IN_FLIGHT")) {
                run.updatePhase(run.getPhase().claim(NOW));
            }
            if (stage.equals("RETRY_WAIT")) {
                run.postponeDispatch(NOW.plusSeconds(3600));
            }
        });
        given(notificationSender.deliver(any())).willReturn(new NotificationDeliveryResult(
                true,
                false,
                null,
                200,
                null
        ));
        // When
        scheduler.apply();
        scheduler.apply();
        // Then
        assertThat(context.getBeansOfType(JevJobDispatcher.class)).isEmpty();
        assertThat(context.getBeansOfType(JevJobPublisher.class)).isEmpty();
        var run = execution.getRun(id);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(run.getEvaluation().error()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(run.getApplicationReason()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(run.getPhase().calls()).isEmpty();
        assertThat(run.getDispatchAttempts()).isZero();
        assertThat(jobs.findDueSlackIds(NOW)).containsExactly(id);
        slack.dispatch();
        slack.dispatch();
        verify(notificationSender, times(1)).deliver(any());
        assertThat(execution.getRun(id).getSlackDelivery().status()).isEqualTo("SENT");
        assertThat(count("SELECT count(*) FROM notifications")).isZero();
    }

    @Test
    @DisplayName("이미지 준비 전에도 중단을 보존하고 준비 뒤 재활성화해도 관리자 검토로 넘긴다")
    void apply_imageNotReady_retainsCancellationAcrossRestart() {
        // Given
        UUID id = createRun("VALIDATING");
        // When
        scheduler.apply();
        // Then
        var run = execution.getRun(id);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
        assertThat(run.getEvaluation().error()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(run.getAppliedAt()).isNull();
        assertThat(run.getSlackDelivery()).isNull();
        assertThat(application.findUnappliedRunIds()).doesNotContain(id);

        // 이미지 준비 후 worker=true인 새 서비스로 재시작한 상황을 실제 DB 트랜잭션으로 검증한다.
        jdbc.update("UPDATE posts SET moderation_status = 'PENDING' WHERE id = ?", run.getPostId());
        applyAfterRestart(id);
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(execution.getRun(id).getApplicationReason()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(jobs.findDueSlackIds(NOW)).containsExactly(id);
        assertThat(execution.getRun(id).getPhase().calls()).isEmpty();
        assertThat(count("SELECT count(*) FROM posts WHERE moderation_status = 'APPROVED'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM notifications")).isZero();
    }

    @Test
    @DisplayName("중단 뒤 늦은 승인 원문과 토큰은 보존하되 재활성화해도 자동 공개하지 않는다")
    void receive_afterWorkerDisabled_preservesEvidenceWithoutApproval() {
        // Given
        UUID id = createRun("PENDING");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var run = jobs.findRunForUpdate(id).orElseThrow();
            var category = jobs.findCategoryForUpdate(run.getClassificationId()).orElseThrow();
            category.updatePhase(category.getPhase().finish(null));
            category.complete(new JevEvaluation(
                    Map.of(),
                    Map.of(),
                    JevDecision.APPROVE,
                    "subject",
                    null,
                    null,
                    null
            ));
        });
        JevJobService enabled = enabledExecution();
        JevJobClaim claim = new TransactionTemplate(transactions)
                .execute(status -> enabled.claim(id));
        assertThat(claim.action()).isEqualTo("INSPECT");
        scheduler.apply();
        // When
        given(clock.instant()).willReturn(NOW.plusSeconds(10));
        new TransactionTemplate(transactions).executeWithoutResult(status -> enabled.receiveResult(
                id,
                "PHOTO",
                new JevCallResult(
                        claim.callId(),
                        null,
                        null,
                        approvedPhoto(),
                        null,
                        200,
                        123L,
                        null
                )
        ));
        applyAfterRestart(id);
        // Then
        var run = execution.getRun(id);
        assertThat(run.getEvaluation().error()).isEqualTo("AUTOMATION_DISABLED");
        assertThat(run.getPhase().calls()).hasSize(1);
        assertThat(run.getPhase().calls().getFirst().rawResponse()).contains("approve");
        assertThat(run.getPhase().calls().getFirst().inputTokens()).isEqualTo(10);
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(count("SELECT count(*) FROM posts WHERE moderation_status = 'APPROVED'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM notifications")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETED", "ADMIN_APPROVED", "IMAGE_FAILED"})
    @DisplayName("삭제되거나 이미 처리된 사진은 중단 정리에서도 건너뛰고 새 알림을 만들지 않는다")
    void apply_sourceNoLongerEligible_skipsWithoutNotification(String source) {
        // Given
        UUID id = createRun("PENDING");
        UUID postId = execution.getRun(id).getPostId();
        if (source.equals("DELETED")) {
            jdbc.update(
                    "UPDATE posts SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    postId
            );
        }
        if (source.equals("ADMIN_APPROVED")) {
            jdbc.update("UPDATE posts SET moderation_status = 'APPROVED' WHERE id = ?", postId);
        }
        if (source.equals("IMAGE_FAILED")) {
            jdbc.update("UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?", postId);
        }
        // When
        scheduler.apply();
        // Then
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.SKIPPED);
        assertThat(execution.getRun(id).getSlackDelivery()).isNull();
        assertThat(count("SELECT count(*) FROM notifications")).isZero();
    }

    @Test
    @DisplayName("미완료 운영 정리는 관리자 시험 작업을 종료하지 않는다")
    void apply_preview_excludesFromRecovery() {
        // Given
        UUID production = createRun("APPROVED");
        UUID preview = new TransactionTemplate(transactions).execute(status -> {
            var run = jobs.findRun(production).orElseThrow();
            return jobs.saveRun(JevModerationRun.preview(
                    run.getPostId(),
                    TOPIC,
                    "초록",
                    run.getStorageKey(),
                    ADMIN,
                    run.getClassificationId(),
                    new JevPromptDraft(
                            "jev-1.14.0",
                            JevPromptFixtures.classification(),
                            JevPromptFixtures.photo()
                    ),
                    NOW
            )).getId();
        });
        // When
        scheduler.apply();
        // Then
        assertThat(execution.getRun(preview).getFinishedAt()).isNull();
        assertThat(application.findUnappliedRunIds()).doesNotContain(preview);
    }

    @Test
    @DisplayName("준비 중 작업이 한 배치보다 많아도 모두 닫고 준비 후 관리자 알림을 예약한다")
    void apply_moreThanOneBatch_closesAllWithoutStarvation() {
        // Given
        for (int index = 0; index < 21; index++) {
            createRun("VALIDATING");
        }
        // When
        scheduler.apply();
        scheduler.apply();
        // Then
        assertThat(count("SELECT count(*) FROM jev_moderation_runs WHERE finished_at IS NOT NULL"))
                .isEqualTo(21);
        assertThat(application.findUnappliedRunIds()).isEmpty();
        assertThat(
                count("SELECT count(*) FROM jev_moderation_runs WHERE slack_delivery IS NOT NULL"))
                .isZero();
        jdbc.update("UPDATE posts SET moderation_status = 'PENDING'");
        scheduler.apply();
        scheduler.apply();
        assertThat(count(
                "SELECT count(*) FROM jev_moderation_runs WHERE disposition = 'ADMIN_REVIEW'"))
                .isEqualTo(21);
        assertThat(
                count("SELECT count(*) FROM jev_moderation_runs WHERE slack_delivery IS NOT NULL"))
                .isEqualTo(21);
    }

    @Test
    @DisplayName("꺼지기 전에 완료된 승인 추천도 원래 추천은 보존하고 관리자에게 넘긴다")
    void apply_completedApproval_keepsOriginalRecommendation() {
        // Given
        UUID id = createRun("PENDING");
        new TransactionTemplate(transactions).executeWithoutResult(status -> jobs
                .findRunForUpdate(id).orElseThrow().complete(new JevEvaluation(
                        Map.of(),
                        Map.of(),
                        JevDecision.APPROVE,
                        null,
                        10L,
                        3L,
                        null
                ), NOW));
        // When
        scheduler.apply();
        // Then
        assertThat(execution.getRun(id).getEvaluation().recommendation())
                .isEqualTo(JevDecision.APPROVE);
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(jobs.findDueSlackIds(NOW)).containsExactly(id);
    }

    private JevPromptVersion createPrompt(JevPromptRole role) {
        JevPromptVersion version = prompts.createVersion(ADMIN,
                new AdminJevPromptVersionService.CreateCommand(
                        role,
                        "jev-1.14.0",
                        role == JevPromptRole.JEV_TOPIC_CLASSIFICATION
                                ? JevPromptFixtures.classification()
                                : JevPromptFixtures.photo(),
                        null
                ));
        prompts.updateActivation(
                ADMIN,
                version.getId(),
                true
        );
        return version;
    }

    /** 중단 전 접수된 자동 작업의 DB 스냅샷을 실제 도메인과 저장소로 준비한다. */
    private UUID createRun(String moderationStatus) {
        UUID user = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        UUID post = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO users(id, email, status, signature_original_storage_key) VALUES (?, ?, 'ACTIVE', ?)",
                user,
                user + "@jev-off.test",
                "chalkak/signatures/test/" + user + ".png"
        );
        jdbc.update(
                "INSERT INTO photos(id, original_storage_key, thumbnail_storage_key) VALUES (?, ?, ?)",
                photoId,
                "chalkak/posts/test/original/" + photoId + ".webp",
                "chalkak/posts/test/thumbnail/" + photoId + ".webp"
        );
        jdbc.update(
                "INSERT INTO posts(id, user_id, topic_id, photo_id, moderation_status) VALUES (?, ?, ?, ?, CAST(? AS moderation_status))",
                post,
                user,
                TOPIC,
                photoId,
                moderationStatus
        );
        return new TransactionTemplate(transactions).execute(status -> {
            var category = jobs.saveCategory(JevTopicCategory.create(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    NOW
            ));
            return jobs.saveRun(JevModerationRun.production(
                    posts.findByIdForUpdate(post).orElseThrow(),
                    classification,
                    photo,
                    category,
                    null,
                    0,
                    NOW
            )).getId();
        });
    }

    private JevJobService enabledExecution() {
        return new JevJobService(
                jobs,
                posts,
                topics,
                parser,
                clock,
                keys,
                new JevAutomationService(versions, true)
        );
    }

    private void applyAfterRestart(UUID id) {
        var enabled = new JevResultApplicationService(
                jobs,
                posts,
                users,
                new JevAutomationService(versions, true),
                notifications,
                clock,
                true
        );
        new TransactionTemplate(transactions).executeWithoutResult(status -> enabled.apply(id));
    }

    private String approvedPhoto() {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (String name : JevPromptFixtures.photo().questions().keySet()) {
            answers.put(name, Map.of(
                    "type",
                    "choice",
                    "choice",
                    "approve",
                    "confidence",
                    1,
                    "probabilities",
                    Map.of(
                            "approve",
                            1,
                            "human_review",
                            0,
                            "reject",
                            0
                    )
            ));
        }
        return mapper.writeValueAsString(Map.of(
                "model",
                "jev-1.14.0",
                "answers",
                answers,
                "usage",
                Map.of(
                        "input_tokens",
                        10,
                        "output_tokens",
                        3
                )
        ));
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private void cleanOwnRows() {
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN);
    }
}
