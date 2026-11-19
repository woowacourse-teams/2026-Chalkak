package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService;
import com.chalkak.backend.admin.service.post.AdminPostCommandService;
import com.chalkak.backend.jev.domain.JevCallResult;
import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevPromptFixtures;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevJobRepository;
import com.chalkak.backend.notification.service.NotificationService;
import com.chalkak.backend.notification.service.NotificationDeliveryResult;
import com.chalkak.backend.jev.infrastructure.infra.JevSlackDispatcher;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Objects;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(properties = {
        "chalkak.jev.worker-enabled=true",
        "chalkak.jev.queue-url=https://sqs.ap-northeast-2.amazonaws.com/123456789012/jev-jobs"
})
class JevProductionModerationTest extends IntegrationTestSupport {

    private static final UUID ADMIN = UUID.fromString("019ad963-213b-734e-a261-60e568c14056");
    private static final UUID USER = UUID.fromString("019ad963-213b-734e-a261-60e568c14057");
    private static final UUID TOPIC = UUID.fromString("019ad963-213b-734e-a261-60e568c14058");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PostCommandService posts;
    @Autowired
    private JevJobRepository jobs;
    @Autowired
    private JevJobService execution;
    @Autowired
    private JevResultApplicationService application;
    @Autowired
    private AdminJevPromptVersionService prompts;
    @Autowired
    private AdminPostCommandService adminPosts;
    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private PlatformTransactionManager transactions;
    @MockitoBean
    private Clock clock;
    @MockitoBean
    private PostImageStorage storage;
    @MockitoBean
    private JevJobPublisher publisher;
    @MockitoSpyBean
    private NotificationService notifications;

    @Autowired
    private JevSlackService slackService;
    @Autowired
    private JevSlackDispatcher slackDispatcher;

    private Instant now;
    private JevPromptVersion classification;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        cleanOwnRows();
        now = Instant.now();
        given(clock.instant()).willReturn(now);
        jdbc.update(
                "INSERT INTO admins(id, username, password) VALUES (?, 'jev-production-test', 'test')",
                ADMIN);
        jdbc.update(
                "INSERT INTO users(id, email, status, signature_original_storage_key) VALUES (?, 'jev-production@test.example', 'ACTIVE', 'chalkak/signatures/test/test.png')",
                USER
        );
        jdbc.update(
                "INSERT INTO topics(id, title, topic_date, starts_at, ends_at) VALUES (?, '초록', ?, ?, ?)",
                TOPIC,
                LocalDate.ofInstant(now, ZoneId.of("Asia/Seoul")),
                Timestamp.from(now.minusSeconds(3600)),
                Timestamp.from(now.plusSeconds(3600))
        );
        given(storage.existsUploadedImage(any())).willReturn(true);
        given(storage.toOriginalStorageKey(any()))
                .willAnswer(call -> "chalkak/posts/test/original/" + call.getArgument(0) + ".webp");
        given(storage.toThumbnailStorageKey(any())).willAnswer(
                call -> "chalkak/posts/test/thumbnail/" + call.getArgument(0) + ".webp");
        classification = createPrompt(JevPromptRole.JEV_TOPIC_CLASSIFICATION);
        JevPromptVersion photo = createPrompt(JevPromptRole.JEV_PHOTO_MODERATION);
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                true
        );
        prompts.updateActivation(
                ADMIN,
                photo.getId(),
                true
        );
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbc).clean();
        cleanOwnRows();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("이미지와 게시물 준비 순서가 달라도 커밋과 함께 작업 하나만 남긴다")
    void register_bothArrivalOrders_createsOneDurableJob(boolean imageFirst) {
        // Given
        UUID upload = insertUpload(imageFirst);
        // When
        UUID post = createPost(upload);
        if (!imageFirst) {
            UUID waiting = job(post);
            assertThat(execution.claim(waiting).action()).isEqualTo("WAIT");
            assertThat(execution.findDueRunIds()).doesNotContain(waiting);
            posts.completePostImageProcessing(upload, Map.of());
        }
        posts.completePostImageProcessing(upload, Map.of());
        // Then
        var run = jobs.findProductionRunByPostId(post).orElseThrow();
        assertThat(run.getClassificationPromptId()).isEqualTo(classification.getId());
        assertThat(run.getPreviewDraft()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jev_moderation_runs WHERE NOT preview",
                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("등록이 롤백되면 검수 작업과 등록 횟수도 남지 않는다")
    void register_rollback_leavesNoJob() {
        // Given
        UUID upload = insertUpload(true);
        TransactionTemplate tx = new TransactionTemplate(transactions);
        // When & Then
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            createPost(upload);
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM jev_moderation_runs", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("세 질문 승인 추천만 자동 승인하며 사용자 알림은 한 건이고 가짜 관리자 감사는 없다")
    void apply_allApproved_publishesOnce() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs",
                Integer.class);
        finish(id, "approve");
        // When
        application.apply(id);
        application.apply(id);
        // Then
        assertThat(status(post)).isEqualTo("APPROVED");
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.AUTO_APPROVED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_logs", Integer.class))
                .isEqualTo(auditBefore);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reject", "human_review"})
    @DisplayName("반려 추천과 검토는 관리자 대기로 남기고 사용자 반려 알림을 만들지 않는다")
    void apply_rejectOrReview_keepsPending(String decision) {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, decision);
        // When
        application.apply(id);
        // Then
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(execution.getRun(id).getEvaluation().recommendation().name())
                .isEqualTo(decision.equals("reject") ? "REJECT" : "HUMAN_REVIEW");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("큐 대기 중 삭제하면 Jev를 부르지 않고 이후에도 복구하거나 알리지 않는다")
    void claim_deletedBeforeStart_skipsWithoutCall() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        posts.deletePost(USER, post);
        // When
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        application.apply(id);
        // Then
        assertThat(execution.getCategory(id).getPhase().calls()).isEmpty();
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.SKIPPED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("진행 중 삭제해도 늦은 원응답과 추천은 보존하고 공개와 알림만 차단한다")
    void receive_deletedInFlight_retainsEvidenceWithoutPublication() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        classify(id);
        JevJobClaim claim = execution.claim(id);
        posts.deletePost(USER, post);
        // When
        result(
                id,
                "PHOTO",
                claim,
                photoResponse("approve")
        );
        application.apply(id);
        // Then
        var run = execution.getRun(id);
        assertThat(run.getPhase().calls()).hasSize(1);
        assertThat(run.getEvaluation().recommendation()).isEqualTo(JevDecision.APPROVE);
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.SKIPPED);
        assertThat(jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM posts WHERE id = ?",
                Boolean.class,
                post
        ))
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("명시 중단 후 재활성화해도 예전 결과는 자동 공개하지 않는다")
    void apply_disabledAndReenabled_keepsOldJobManual() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                false
        );
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                true
        );
        // When
        application.apply(id);
        // Then
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(execution.getRun(id).getApplicationReason()).isEqualTo("AUTOMATION_DISABLED");
        posts.deletePost(USER, post);
        UUID next = createPost(insertUpload(true));
        UUID nextId = job(next);
        // 같은 분류 버전의 성공 결과는 새 등록에서도 재사용한다.
        assertThat(execution.claim(nextId).action()).isEqualTo("INSPECT");
    }

    @Test
    @DisplayName("필수 프롬프트가 없으면 등록은 성공하고 호출 없이 관리자 대기로 남는다")
    void register_missingPrompt_acceptsManualReview() {
        // Given
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                false
        );
        // When
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        application.apply(id);
        // Then
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
    }

    @Test
    @DisplayName("자동 검수를 켜기 전에 접수한 사진은 늦게 준비되어도 수동 대기로 유지한다")
    void register_enableWhileImagePending_doesNotBackfill() {
        // Given
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                false
        );
        UUID upload = insertUpload(false);
        UUID post = createPost(upload);
        UUID id = job(post);
        // When
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                true
        );
        posts.completePostImageProcessing(upload, Map.of());
        application.apply(id);
        // Then
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        assertThat(execution.getRun(id).getClassificationPromptId()).isNull();
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(status(post)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("이미지 준비 중 새 프롬프트를 적용해도 등록 당시 프롬프트 참조를 유지한다")
    void register_promptReplacedWhilePreparing_keepsCapturedVersion() {
        // Given
        UUID upload = insertUpload(false);
        UUID post = createPost(upload);
        UUID id = job(post);
        UUID original = execution.getRun(id).getPhotoPromptId();
        JevPromptVersion replacement = createPrompt(JevPromptRole.JEV_PHOTO_MODERATION);
        prompts.updateActivation(
                ADMIN,
                replacement.getId(),
                true
        );
        // When
        posts.completePostImageProcessing(upload, Map.of());
        finish(id, "approve");
        application.apply(id);
        // Then
        assertThat(execution.getRun(id).getPhotoPromptId()).isEqualTo(original);
        assertThat(original).isNotEqualTo(replacement.getId());
        assertThat(status(post)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("도입 전 등록 상태처럼 작업 예약이 없는 사진은 준비 후에도 자동 대상으로 소급하지 않는다")
    void register_legacyValidatingPost_createsManualJobOnly() {
        // Given: 구버전 서버에서 등록되어 Jev 예약이 없는 VALIDATING 상태를 구성한다.
        UUID upload = insertUpload(false);
        UUID post = createPost(upload);
        jdbc.update("DELETE FROM jev_moderation_runs WHERE post_id = ?", post);
        // When
        posts.completePostImageProcessing(upload, Map.of());
        UUID id = job(post);
        application.apply(id);
        // Then
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        assertThat(execution.getRun(id).getApplicationReason())
                .isEqualTo("PREEXISTING_REGISTRATION");
        assertThat(execution.getRun(id).getClassificationPromptId()).isNull();
        assertThat(status(post)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("분류 API 영구 오류는 사진 요청 없이 관리자 대기로 남고 반려 알림을 만들지 않는다")
    void apply_classificationApiFailure_keepsTechnicalReview() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        JevJobClaim claim = execution.claim(id);
        execution.receiveResult(
                id,
                "CLASSIFICATION",
                new JevCallResult(
                        claim.callId(),
                        null,
                        null,
                        "{}",
                        "HTTP_ERROR",
                        401,
                        20L,
                        null
                )
        );
        // When
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        application.apply(id);
        // Then
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(execution.getRun(id).getEvaluation().error()).isEqualTo("CLASSIFICATION_FAILED");
        assertThat(execution.getRun(id).getPhase().calls()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("관리자가 먼저 결정했으면 늦은 모델 결과가 사용자 알림을 중복 생성하지 않는다")
    void apply_adminAlreadyApproved_skips() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        adminPosts.moderate(
                post,
                ADMIN,
                ModerationStatus.APPROVED,
                null
        );
        // When
        application.apply(id);
        // Then
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.SKIPPED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("알림 저장 실패는 승인과 적용 기록까지 함께 롤백한다")
    void apply_notificationFailure_rollsBackApproval() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        NotificationService target = AopTestUtils.getUltimateTargetObject(notifications);
        doThrow(new IllegalStateException("notification insert failed")).when(target)
                .createForModeration(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                );
        // When & Then
        assertThatThrownBy(() -> application.apply(id)).isInstanceOf(IllegalStateException.class);
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(execution.getRun(id).getAppliedAt()).isNull();
    }

    @Test
    @DisplayName("실제 DB에서 중복 결과 적용 요청도 승인 알림 하나만 만든다")
    void apply_concurrentRequests_createsOneNotification() throws Exception {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                application.apply(id);
                return true;
            });
            var second = executor.submit(() -> {
                start.await();
                application.apply(id);
                return true;
            });
            // When
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
        }
        // Then
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("자동 승인에는 Slack이 없고 반려 추천은 결과 후 DB 알림만 예약한다")
    void slack_rejectRecommendation_isDurableAndDoesNotSendInitially() {
        UUID id = job(createPost(insertUpload(true)));
        finish(id, "reject");
        application.apply(id);
        assertThat(execution.getRun(id).getSlackDelivery().status()).isEqualTo("PENDING");
        verifyNoInteractions(notificationSender);
        given(notificationSender.deliver(any())).willAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return delivery(true, false);
        });
        slackDispatcher.deliver(id);
        slackDispatcher.deliver(id);
        assertThat(execution.getRun(id).getSlackDelivery().status()).isEqualTo("SENT");
        assertThat(status(execution.getRun(id).getPostId())).isEqualTo("PENDING");
        verify(notificationSender, times(1)).deliver(any());
    }

    @Test
    @DisplayName("Slack 재시도는 DB를 다시 읽고 1분·5분 대기하며 최초 포함 세 번에 멈춘다")
    void slack_transientFailure_retriesThreeTimesWithoutModelCall() {
        UUID id = job(createPost(insertUpload(true)));
        finish(id, "reject");
        application.apply(id);
        var first = slackService.claim(id);
        assertThat(slackService.claim(id)).isNull();
        slackService.finish(
                id,
                first.token(),
                delivery(false, true)
        );
        assertThat(slackService.findDueIds()).doesNotContain(id);
        given(clock.instant()).willReturn(now.plusSeconds(60));
        var second = slackService.claim(id);
        assertThat(second).isNotNull();
        slackService.finish(
                id,
                second.token(),
                delivery(false, true)
        );
        // 이전 시도의 성공 응답이 늦게 와도 새 시도를 성공으로 바꾸지 않는다.
        slackService.finish(
                id,
                first.token(),
                delivery(true, false)
        );
        given(clock.instant()).willReturn(now.plusSeconds(360));
        var third = slackService.claim(id);
        slackService.finish(
                id,
                third.token(),
                delivery(false, true)
        );
        assertThat(execution.getRun(id).getSlackDelivery().status()).isEqualTo("FAILED");
        assertThat(execution.getRun(id).getSlackDelivery().history()).hasSize(3);
        assertThat(slackService.findDueIds()).doesNotContain(id);
        assertThat(execution.getRun(id).getPhase().calls()).hasSize(1);
    }

    @Test
    @DisplayName("삭제된 사진의 대기 Slack은 보내지 않고 이력만 남긴다")
    void slack_deletedPost_skipsDelivery() {
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "reject");
        application.apply(id);
        posts.deletePost(USER, post);
        slackDispatcher.deliver(id);
        assertThat(execution.getRun(id).getSlackDelivery().status()).isEqualTo("SKIPPED");
        verifyNoInteractions(notificationSender);
    }

    @Test
    @DisplayName("동시 Slack 확보 요청은 실제 DB에서 전송권 하나만 발급한다")
    void slack_concurrentClaims_reservesOnce() throws Exception {
        UUID id = job(createPost(insertUpload(true)));
        finish(id, "reject");
        application.apply(id);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return slackService.claim(id);
            });
            var second = executor.submit(() -> {
                start.await();
                return slackService.claim(id);
            });
            start.countDown();
            var claims = java.util.stream.Stream.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            ).filter(Objects::nonNull).toList();
            assertThat(claims).hasSize(1);
        }
        assertThat(execution.getRun(id).getSlackDelivery().attempts()).isEqualTo(1);
    }

    private NotificationDeliveryResult delivery(boolean sent, boolean retryable) {
        return new NotificationDeliveryResult(
                sent,
                retryable,
                sent ? null : "SLACK_NETWORK_ERROR",
                sent ? 200 : null,
                null
        );
    }

    private JevPromptVersion createPrompt(JevPromptRole role) {
        return prompts.createVersion(ADMIN, new AdminJevPromptVersionService.CreateCommand(
                role,
                "jev-1.14.0",
                role == JevPromptRole.JEV_TOPIC_CLASSIFICATION
                        ? JevPromptFixtures.classification()
                        : JevPromptFixtures.photo(),
                null
        ));
    }

    private UUID insertUpload(boolean ready) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO post_image_uploads(id, user_id, status, image_metadata, expires_at) VALUES (?, ?, CAST(? AS post_image_upload_status), CAST(? AS jsonb), ?)",
                id,
                USER,
                ready ? "READY" : "ISSUED",
                ready ? "{}" : null,
                Timestamp.from(now.plusSeconds(3600))
        );
        return id;
    }

    private UUID createPost(UUID upload) {
        return posts.createPost(
                USER,
                TOPIC,
                upload,
                null,
                null,
                false,
                null
        ).postId();
    }

    private UUID job(UUID post) {
        return jobs.findProductionRunByPostId(post).orElseThrow().getId();
    }

    private String status(UUID post) {
        return jdbc.queryForObject(
                "SELECT moderation_status FROM posts WHERE id = ?",
                String.class,
                post
        );
    }

    private void classify(UUID id) {
        String raw = response(Map.of("topic_category", Map.of(
                "type",
                "choice",
                "choice",
                "subject",
                "confidence",
                1,
                "probabilities",
                Map.of(
                        "subject",
                        1,
                        "general",
                        0
                )
        )));
        result(
                id,
                "CLASSIFICATION",
                execution.claim(id),
                raw
        );
    }

    private void finish(UUID id, String decision) {
        classify(id);
        result(
                id,
                "PHOTO",
                execution.claim(id),
                photoResponse(decision)
        );
    }

    private void result(
            UUID id,
            String stage,
            JevJobClaim claim,
            String raw
    ) {
        execution.receiveResult(
                id,
                stage,
                new JevCallResult(
                        claim.callId(),
                        null,
                        null,
                        raw,
                        null,
                        200,
                        123L,
                        null
                )
        );
    }

    private String photoResponse(String decision) {
        Map<String, Object> answers = new LinkedHashMap<>();
        for (String name : JevPromptFixtures.photo().questions().keySet()) {
            answers.put(name, Map.of(
                    "type",
                    "choice",
                    "choice",
                    decision,
                    "confidence",
                    1,
                    "probabilities",
                    Map.of(
                            "approve",
                            decision.equals("approve") ? 1 : 0,
                            "human_review",
                            decision.equals("human_review") ? 1 : 0,
                            "reject",
                            decision.equals("reject") ? 1 : 0
                    )
            ));
        }
        return response(answers);
    }

    private String response(Map<String, Object> answers) {
        return mapper.writeValueAsString(Map.of(
                "model",
                "jev-1.14.0",
                "answers",
                answers
        ));
    }

    private void cleanOwnRows() {
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN);
    }
}
