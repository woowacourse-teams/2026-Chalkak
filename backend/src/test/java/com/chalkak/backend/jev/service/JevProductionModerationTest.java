package com.chalkak.backend.jev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.BDDMockito.given;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.admin.service.jev.AdminJevPromptVersionService;
import com.chalkak.backend.admin.service.jev.AdminJevEvaluationService;
import com.chalkak.backend.admin.service.jev.AdminJevMetricsService;
import com.chalkak.backend.admin.service.jev.AdminJevQueryService;
import com.chalkak.backend.admin.service.post.AdminPostCorrectionService;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import com.chalkak.backend.jev.repository.JevRunFilter;
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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.slf4j.LoggerFactory;
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

    @Autowired
    private AdminJevEvaluationService evaluations;
    @Autowired
    private AdminJevMetricsService metrics;
    @Autowired
    private AdminJevQueryService history;
    @Autowired
    private AdminPostCorrectionService corrections;
    @MockitoSpyBean
    private AdminAuditLogService audits;

    private Instant now;
    private JevPromptVersion classification;
    private final Logger moderationLogger = (Logger) LoggerFactory.getLogger("chalkak.moderation");
    private final ListAppender<ILoggingEvent> moderationAppender = new ListAppender<>();

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
        moderationAppender.start();
        moderationLogger.addAppender(moderationAppender);
    }

    @AfterEach
    void tearDown() {
        moderationLogger.detachAppender(moderationAppender);
        moderationAppender.stop();
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
    @DisplayName("자동 검수가 켜져 있으면 미완료 작업은 관리자 정리 대상으로 가져오지 않는다")
    void findUnapplied_workerEnabled_excludesUnfinished() {
        // Given
        UUID id = job(createPost(insertUpload(true)));
        // When & Then
        assertThat(application.findUnappliedRunIds()).doesNotContain(id);
        application.apply(id);
        assertThat(execution.getRun(id).getFinishedAt()).isNull();
        assertThat(execution.getRun(id).getSlackDelivery()).isNull();
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
        Instant approvedAt = now.plusSeconds(2).truncatedTo(ChronoUnit.MILLIS);
        given(clock.instant()).willReturn(approvedAt);
        jdbc.update(
                "UPDATE posts SET created_at = ? WHERE id = ?",
                Timestamp.from(approvedAt.minusSeconds(37)),
                post
        );
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
        assertThat(approvedEvents()).hasSize(1);
        ILoggingEvent event = approvedEvents().getFirst();
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(keyValue(event, "type")).isEqualTo("moderation");
        assertThat(keyValue(event, "postId")).isEqualTo(post);
        assertThat(keyValue(event, "waitSeconds")).isEqualTo(37L);
        assertThat(event.getKeyValuePairs()).hasSize(4);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("실제 승인 트랜잭션이 커밋될 때만 완료 로그를 남기고 롤백이면 남기지 않는다")
    void apply_outerTransaction_logsOnlyAfterCommit(boolean commit) {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        // When
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            application.apply(id);
            assertThat(approvedEvents()).isEmpty();
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        // Then
        assertThat(approvedEvents()).hasSize(commit ? 1 : 0);
        assertThat(status(post)).isEqualTo(commit ? "APPROVED" : "PENDING");
        assertThat(execution.getRun(id).getAppliedAt() != null).isEqualTo(commit);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(commit ? 1 : 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"QueueUnavailable", "ACCEPTED_WITHOUT_CLAIM"})
    @DisplayName("작업 전달 실패와 큐 수락 후 무응답 모두 상한에 도달하면 관리자 대기로 한 번만 전환한다")
    void reserveDispatch_noClaim_finishesForAdmin(String delivery) {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        for (int attempt = 1; attempt <= 4; attempt++) {
            given(clock.instant()).willReturn(now.plusSeconds((attempt - 1) * 15L));
            assertThat(execution.reserveDispatch(id)).isEqualTo(attempt);
            execution.recordDispatch(
                    id,
                    attempt,
                    delivery.equals("QueueUnavailable") ? delivery : null
            );
        }
        // When
        given(clock.instant()).willReturn(now.plusSeconds(60));
        assertThat(execution.reserveDispatch(id)).isZero();
        assertThat(application.findUnappliedRunIds()).contains(id);
        application.apply(id);
        var firstSlack = execution.getRun(id).getSlackDelivery();
        application.apply(id);
        // Then
        var run = execution.getRun(id);
        assertThat(run.getDispatchAttempts()).isEqualTo(4);
        assertThat(run.getUnclaimedDispatchAttempts()).isEqualTo(4);
        assertThat(run.getDispatchError())
                .isEqualTo(delivery.equals("QueueUnavailable") ? delivery : null);
        assertThat(run.getEvaluation().error()).isEqualTo("DISPATCH_LIMIT_EXCEEDED");
        assertThat(run.getEvaluation().recommendation()).isEqualTo(JevDecision.HUMAN_REVIEW);
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(run.getSlackDelivery()).isEqualTo(firstSlack);
        assertThat(jobs.findDueSlackIds(clock.instant())).containsExactly(id);
        assertThat(run.getPhase().calls()).isEmpty();
        assertThat(execution.getCategory(id).getPhase().calls()).isEmpty();
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
        assertThat(execution.reserveDispatch(id)).isZero();
        assertThat(execution.findDueRunIds()).doesNotContain(id);
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("마지막 전달에서 시작한 주제 분류도 기존 100초 기한을 줄이지 않는다")
    void reserveDispatch_classificationInFlight_waitsUntilPhaseDeadline() {
        // Given
        UUID id = job(createPost(insertUpload(true)));
        JevJobClaim claim = execution.claim(id);
        assertThat(claim.action()).isEqualTo("CLASSIFY");
        for (int attempt = 1; attempt <= 4; attempt++) {
            given(clock.instant()).willReturn(now.plusSeconds(attempt * 15L));
            assertThat(execution.reserveDispatch(id)).isEqualTo(attempt);
        }
        // When
        given(clock.instant()).willReturn(now.plusSeconds(75));
        assertThat(execution.reserveDispatch(id)).isZero();
        // Then
        assertThat(execution.getRun(id).getFinishedAt()).isNull();
        assertThat(execution.getRun(id).getDispatchAt()).isEqualTo(claim.deadline());
        given(clock.instant()).willReturn(claim.deadline());
        assertThat(execution.reserveDispatch(id)).isZero();
        assertThat(execution.getRun(id).getEvaluation().error())
                .isEqualTo("DISPATCH_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("이미지 준비를 오래 기다려도 전달 횟수는 소모하지 않고 준비된 뒤 첫 전달을 시작한다")
    void reserveDispatch_imageNotReady_doesNotUseDeliveryBudget() {
        // Given
        UUID post = createPost(insertUpload(false));
        UUID id = job(post);
        given(clock.instant()).willReturn(now.plusSeconds(600));
        assertThat(execution.findDueRunIds()).doesNotContain(id);
        assertThat(execution.getRun(id).getDispatchAttempts()).isZero();
        // When
        jdbc.update("UPDATE posts SET moderation_status = 'PENDING' WHERE id = ?", post);
        // Then
        assertThat(execution.findDueRunIds()).contains(id);
        assertThat(execution.reserveDispatch(id)).isEqualTo(1);
        assertThat(execution.getRun(id).getFinishedAt()).isNull();
    }

    @Test
    @DisplayName("전달 한도 직전의 동시 예약은 마지막 전달 한 건만 확보하고 한도를 넘기지 않는다")
    void reserveDispatch_lastDeliveryConcurrent_reservesOne() throws Exception {
        // Given
        UUID id = job(createPost(insertUpload(true)));
        jdbc.update("""
                UPDATE jev_moderation_runs
                SET dispatch_attempts = 19, unclaimed_dispatch_attempts = 3 WHERE id = ?
                """, id);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return execution.reserveDispatch(id);
            });
            var second = executor.submit(() -> {
                start.await();
                return execution.reserveDispatch(id);
            });
            // When
            start.countDown();
            // Then
            assertThat(java.util.List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            )).containsExactlyInAnyOrder(20, 0);
        }
        assertThat(execution.getRun(id).getDispatchAttempts()).isEqualTo(20);
        assertThat(execution.getRun(id).getUnclaimedDispatchAttempts()).isEqualTo(4);
        given(clock.instant()).willReturn(now.plusSeconds(15));
        assertThat(execution.reserveDispatch(id)).isZero();
        assertThat(execution.getRun(id).getEvaluation().error())
                .isEqualTo("DISPATCH_LIMIT_EXCEEDED");
    }

    @Test
    @DisplayName("전달 상한 종료 후 늦은 사진 응답은 증거로 남기고 관리자 검토를 자동 승인으로 바꾸지 않는다")
    void receive_afterDispatchLimit_preservesEvidenceWithoutApproval() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        classify(id);
        JevJobClaim claim = execution.claim(id);
        jdbc.update("UPDATE jev_moderation_runs SET unclaimed_dispatch_attempts = 4 WHERE id = ?",
                id);
        given(clock.instant()).willReturn(now.plusSeconds(100));
        assertThat(execution.reserveDispatch(id)).isZero();
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
        assertThat(run.getPhase().calls().getFirst().rawResponse()).contains("approve");
        assertThat(run.getPhase().calls().getFirst().inputTokens()).isEqualTo(10);
        assertThat(run.getEvaluation().error()).isEqualTo("DISPATCH_LIMIT_EXCEEDED");
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.ADMIN_REVIEW);
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("정상 확보 응답이 있으면 누적 전달이 4회를 넘어도 모델 예산을 초기화하지 않고 검수를 마친다")
    void reserveDispatch_claimAcknowledged_preservesModelBudget() {
        // Given
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        classify(id);
        JevJobClaim photo = execution.claim(id);
        Instant deadline = photo.deadline();
        // When
        for (int attempt = 1; attempt <= 6; attempt++) {
            given(clock.instant()).willReturn(now.plusSeconds(attempt * 15L));
            assertThat(execution.reserveDispatch(id)).isEqualTo(attempt);
            JevJobClaim next = execution.claim(id);
            assertThat(next.action()).isIn("WAIT", "INSPECT");
            if (next.action().equals("INSPECT")) {
                photo = next;
            }
            var run = execution.getRun(id);
            assertThat(run.getUnclaimedDispatchAttempts()).isZero();
            assertThat(run.getPhase().deadline()).isEqualTo(deadline);
            assertThat(run.getFinishedAt()).isNull();
        }
        result(
                id,
                "PHOTO",
                photo,
                photoResponse("approve")
        );
        application.apply(id);
        // Then
        var run = execution.getRun(id);
        assertThat(run.getDispatchAttempts()).isEqualTo(6);
        assertThat(run.getPhase().calls()).hasSize(2);
        assertThat(run.getPhase().calls().getFirst().errorCode()).isEqualTo("RESPONSE_LOST");
        assertThat(run.getDisposition()).isEqualTo(JevDisposition.AUTO_APPROVED);
        assertThat(status(post)).isEqualTo("APPROVED");
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
        assertThat(approvedEvents()).isEmpty();
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
        assertThat(approvedEvents()).isEmpty();
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
        posts.deletePost(USER, post);
        given(clock.instant()).willReturn(now.plusSeconds(1800));
        UUID next = job(createPost(insertUpload(true)));
        assertThat(execution.getCategory(next).getId())
                .isEqualTo(execution.getCategory(id).getId());
        assertThat(execution.claim(next).action()).isEqualTo("STOP");
    }

    @ParameterizedTest
    @ValueSource(ints = {105, 900})
    @DisplayName("일시적 분류 실패는 대기 후 새 분류를 한 번만 만들고 이전 호출 이력을 보존한다")
    void register_transientClassificationFailure_recoversOnceAfterCooldown(int retryAfterSeconds) {
        // Given
        UUID original = createPost(insertUpload(true));
        UUID originalRun = job(original);
        failClassificationWithRateLimit(originalRun, retryAfterSeconds);
        application.apply(originalRun);
        var originalCategory = execution.getCategory(originalRun);
        posts.deletePost(USER, original);

        // When & Then
        long delay = Math.max(600, retryAfterSeconds);
        given(clock.instant()).willReturn(now.plusSeconds(delay - 1));
        UUID early = createPost(insertUpload(true));
        assertThat(execution.getCategory(job(early)).getId()).isEqualTo(originalCategory.getId());
        assertThat(execution.claim(job(early)).action()).isEqualTo("STOP");
        posts.deletePost(USER, early);

        given(clock.instant()).willReturn(now.plusSeconds(delay));
        UUID replacement = createPost(insertUpload(true));
        UUID replacementRun = job(replacement);
        assertThat(execution.getCategory(replacementRun).getId())
                .isNotEqualTo(originalCategory.getId());
        failClassificationWithRateLimit(replacementRun, 600);
        posts.deletePost(USER, replacement);

        given(clock.instant()).willReturn(now.plusSeconds(1800));
        UUID later = job(createPost(insertUpload(true)));
        assertThat(execution.getCategory(later).getId())
                .isEqualTo(execution.getCategory(replacementRun).getId());
        assertThat(execution.claim(later).action()).isEqualTo("STOP");
        assertThat(execution.getCategory(originalRun).getPhase().calls())
                .containsExactlyElementsOf(originalCategory.getPhase().calls());
        assertThat(execution.getRun(originalRun).getDisposition())
                .isEqualTo(JevDisposition.ADMIN_REVIEW);
    }

    private void failClassificationWithRateLimit(UUID id, int retryAfterSeconds) {
        JevJobClaim claim = execution.claim(id);
        assertThat(claim.action()).isEqualTo("CLASSIFY");
        execution.receiveResult(
                id,
                "CLASSIFICATION",
                new JevCallResult(
                        claim.callId(),
                        null,
                        null,
                        "{}",
                        "HTTP_ERROR",
                        429,
                        20L,
                        retryAfterSeconds
                )
        );
        assertThat(execution.claim(id).action()).isEqualTo("STOP");
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
        moderationAppender.list.clear();
        // When
        application.apply(id);
        // Then
        assertThat(execution.getRun(id).getDisposition()).isEqualTo(JevDisposition.SKIPPED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(1);
        assertThat(approvedEvents()).isEmpty();
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
        assertThat(approvedEvents()).isEmpty();
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
        assertThat(approvedEvents()).hasSize(1);
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

    @Test
    @DisplayName("정답 등록과 수정은 감사를 남기고 AI 추천/게시물/사용자 알림을 바꾸지 않는다")
    void evaluate_labelChanges_areIndependentOfPublication() {
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "reject");
        evaluate(id, JevDecision.REJECT);
        evaluate(id, JevDecision.APPROVE);
        assertThat(execution.getRun(id).getGoldLabel().decision()).isEqualTo(JevDecision.APPROVE);
        assertThat(execution.getRun(id).getEvaluation().recommendation())
                .isEqualTo(JevDecision.REJECT);
        assertThat(status(post)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE action = 'JEV_EVALUATION_UPDATED'",
                Integer.class
        )).isEqualTo(2);
    }

    @Test
    @DisplayName("평가 감사 저장이 실패하면 정답도 롤백한다")
    void evaluate_auditFailure_rollsBackLabel() {
        UUID id = job(createPost(insertUpload(true)));
        finish(id, "reject");
        AdminAuditLogService target = AopTestUtils.getUltimateTargetObject(audits);
        doThrow(new IllegalStateException("audit failed")).when(target).createAuditLog(any());
        assertThatThrownBy(() -> evaluate(id, JevDecision.APPROVE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(execution.getRun(id).getGoldLabel()).isNull();
    }

    @Test
    @DisplayName("실제 정정은 양방향 사유를 사용자에게 보내고 중복 요청은 새 알림을 만들지 않는다")
    void correct_actualDecision_sendsReasonWithoutOverwritingRecommendation() {
        UUID post = createPost(insertUpload(true));
        UUID id = job(post);
        finish(id, "approve");
        application.apply(id);
        corrections.correct(
                post,
                ADMIN,
                ModerationStatus.REJECTED,
                "촬영 기준 재확인"
        );
        corrections.correct(
                post,
                ADMIN,
                ModerationStatus.REJECTED,
                "촬영 기준 재확인"
        );
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(2);
        corrections.correct(
                post,
                ADMIN,
                ModerationStatus.APPROVED,
                "관리자 재확인으로 승인 정정"
        );
        assertThat(status(post)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifications", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT body FROM notifications WHERE body LIKE '정정 사유:%'",
                String.class))
                .containsExactlyInAnyOrder("정정 사유: 촬영 기준 재확인", "정정 사유: 관리자 재확인으로 승인 정정");
        assertThat(execution.getRun(id).getEvaluation().recommendation())
                .isEqualTo(JevDecision.APPROVE);
        assertThat(execution.getRun(id).getGoldLabel()).isNull();
    }

    @Test
    @DisplayName("정정 알림 저장 실패는 상태/감사를 함께 롤백한다")
    void correct_notificationFailure_rollsBackEverything() {
        UUID post = createPost(insertUpload(true));
        finish(job(post), "approve");
        application.apply(job(post));
        NotificationService target = AopTestUtils.getUltimateTargetObject(notifications);
        doThrow(new IllegalStateException("notification failed")).when(target).createForCorrection(
                any(),
                any(),
                any(),
                any(),
                any()
        );
        assertThatThrownBy(() -> corrections.correct(
                post,
                ADMIN,
                ModerationStatus.REJECTED,
                "검수 정정"
        )).isInstanceOf(IllegalStateException.class);
        assertThat(status(post)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM admin_audit_logs WHERE action = 'POST_MODERATION_CORRECTED'",
                Integer.class
        )).isZero();
    }

    @Test
    @DisplayName("반려 이후 새 활성 게시물이 있으면 이전 사진을 승인하지 않는다")
    void correct_oldRejectedPhotoWithReplacement_rejects() {
        UUID old = createPost(insertUpload(true));
        adminPosts.moderate(
                old,
                ADMIN,
                ModerationStatus.REJECTED,
                "주제 불일치"
        );
        UUID replacement = createPost(insertUpload(true));
        assertThatThrownBy(() -> corrections.correct(
                old,
                ADMIN,
                ModerationStatus.APPROVED,
                "재검토"
        )).isInstanceOf(com.chalkak.backend.exception.BusinessException.class);
        assertThat(status(old)).isEqualTo("REJECTED");
        assertThat(status(replacement)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("삭제된 사진을 정정으로 복구하지 않는다")
    void correct_deletedPost_rejects() {
        UUID post = createPost(insertUpload(true));
        finish(job(post), "approve");
        application.apply(job(post));
        posts.deletePost(USER, post);
        assertThatThrownBy(() -> corrections.correct(
                post,
                ADMIN,
                ModerationStatus.REJECTED,
                "재검토"
        )).isInstanceOf(com.chalkak.backend.exception.BusinessException.class);
    }

    @Test
    @DisplayName("실제 DB 지표는 공유 분류를 한 번만 합산하고 평가 표본/질문 분모를 구분한다")
    void metrics_sharedClassification_countsPhysicalCallsOnce() {
        UUID first = createPost(insertUpload(true));
        UUID firstRun = job(first);
        finish(firstRun, "approve");
        application.apply(firstRun);
        evaluate(firstRun, JevDecision.REJECT);
        posts.deletePost(USER, first);
        UUID secondRun = job(createPost(insertUpload(true)));
        result(
                secondRun,
                "PHOTO",
                execution.claim(secondRun),
                photoResponse("approve")
        );
        application.apply(secondRun);
        var result = metrics.get(
                now.minusSeconds(1),
                now.plusSeconds(1),
                false
        );
        assertThat(result.runs()).isEqualTo(2);
        assertThat(result.usage().reservedAttempts()).isEqualTo(3);
        assertThat(result.usage().knownInputTokens()).isEqualTo(30);
        assertThat(result.usage().knownOutputTokens()).isEqualTo(9);
        assertThat(result.usage().httpP99Ms()).isEqualTo(123);
        assertThat(result.falseAutoApprovals()).isEqualTo(1);
        assertThat(result.finalAgreement().rated()).isEqualTo(1);
        assertThat(result.questions().get("topic_fit").rated()).isZero();
        var list = history.list(
                new JevRunFilter(
                        false,
                        first,
                        TOPIC,
                        JevDisposition.AUTO_APPROVED
                ),
                0,
                20
        );
        assertThat(list.rows()).hasSize(1);
        assertThat(list.rows().getFirst().postDeletedAt()).isNotNull();
        assertThat(list.rows().getFirst().run().getGoldLabel().decision())
                .isEqualTo(JevDecision.REJECT);
    }

    @Test
    @DisplayName("분류 없이 수동 대기된 이력의 상세도 null 분류로 정상 조회한다")
    void history_manualRunWithNoClassification_returnsDetail() {
        prompts.updateActivation(
                ADMIN,
                classification.getId(),
                false
        );
        UUID id = job(createPost(insertUpload(true)));
        var detail = history.get(id);
        assertThat(detail.category()).isNull();
        assertThat(com.chalkak.backend.admin.api.v1.dto.response.JevModerationDetailResponse
                .from(detail)
                .result().classificationCalls()).isNull();
        assertThat(execution.getCategory(id)).isNull();
    }

    private List<ILoggingEvent> approvedEvents() {
        return moderationAppender.list.stream()
                .filter(event -> "approved".equals(keyValue(event, "event")))
                .toList();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        if (event.getKeyValuePairs() == null) {
            return null;
        }
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst().map(pair -> pair.value).orElse(null);
    }

    private void evaluate(UUID id, JevDecision expected) {
        evaluations.evaluate(
                id,
                ADMIN,
                expected,
                Map.of(),
                "관리자가 사진을 직접 확인",
                JevGoldLabel.Sampling.RANDOM
        );
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

    private void cleanOwnRows() {
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM ai_prompt_versions WHERE created_by_admin_id = ?", ADMIN);
        jdbc.update("DELETE FROM admins WHERE id = ?", ADMIN);
    }
}
