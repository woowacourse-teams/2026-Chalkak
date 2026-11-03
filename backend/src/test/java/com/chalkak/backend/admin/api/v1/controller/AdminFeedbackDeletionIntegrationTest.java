package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.service.feedback.AdminFeedbackCommandService;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.feedback.service.FeedbackService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminFeedbackDeletionIntegrationTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000921");
    private static final Instant NOW = Instant.parse("2026-11-03T12:00:00.123456789Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;
    @Autowired
    private AdminFeedbackCommandService commandService;
    @Autowired
    private FeedbackService feedbackService;
    @Autowired
    private FeedbackRepository feedbackRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @MockitoBean
    private Clock clock;

    private UUID userId;
    private UUID feedbackId;

    @BeforeEach
    void setUp() {
        clean();
        given(clock.instant()).willReturn(NOW);
        userId = UUID.randomUUID();
        feedbackId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO admins (id, username, password) VALUES (?, ?, ?)",
                ADMIN_ID,
                "feedback-delete-admin",
                "test-password"
        );
        jdbcTemplate.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                userId,
                userId + "@chalkak.test",
                "signatures/" + userId
        );
        insertFeedback(feedbackId, NOW.minusSeconds(120));
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @ParameterizedTest
    @EnumSource(AdminUserStatus.class)
    @DisplayName("관리자는 정상·정지·탈퇴 회원의 문의를 삭제하고 원문 없이 감사 전이를 기록한다")
    void deleteFeedback_authorStates_commitsDeletionAndAudit(AdminUserStatus state)
            throws Exception {
        // Given
        jdbcTemplate.update(
                "UPDATE users SET status = CAST(? AS user_status), deleted_at = ? WHERE id = ?",
                state == AdminUserStatus.BANNED ? "BANNED" : "ACTIVE",
                state == AdminUserStatus.WITHDRAWN ? Timestamp.from(NOW) : null,
                userId
        );
        Map<String, Object> original = jdbcTemplate.queryForMap(
                "SELECT user_id, content, created_at FROM feedbacks WHERE id = ?", feedbackId);

        // When
        String requestId = mockMvc
                .perform(delete("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
                        .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getHeader("X-Request-Id");

        // Then
        Instant expected = NOW.truncatedTo(ChronoUnit.MICROS);
        assertThat(deletedAt()).isEqualTo(expected);
        assertThat(jdbcTemplate.queryForMap(
                "SELECT user_id, content, created_at FROM feedbacks WHERE id = ?", feedbackId))
                .isEqualTo(original);
        assertThat(auditCount()).isEqualTo(1);
        Map<String, Object> audit = jdbcTemplate.queryForMap(
                "SELECT actor_admin_id, target_id, reason, request_id FROM admin_audit_logs WHERE target_id = ?",
                feedbackId);
        assertThat(audit.get("actor_admin_id")).isEqualTo(ADMIN_ID);
        assertThat(audit.get("target_id")).isEqualTo(feedbackId);
        assertThat(audit.get("reason")).isNull();
        assertThat(audit.get("request_id")).isEqualTo(UUID.fromString(requestId));
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT before_state = '{"deletedAt":null}'::jsonb
                            AND after_state = CAST(? AS jsonb)
                        FROM admin_audit_logs WHERE target_id = ?
                        """,
                Boolean.class,
                "{\"deletedAt\":\"" + expected + "\"}",
                feedbackId
        )).isTrue();
        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .param("action", "FEEDBACK_DELETED")
                .param("targetType", "FEEDBACK")
                .param("targetId", feedbackId.toString())
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.auditLogs.length()").value(1))
                .andExpect(jsonPath("$.auditLogs[0].action").value("FEEDBACK_DELETED"));
    }

    @Test
    @DisplayName("관리자가 최근 문의를 모두 삭제해도 본인 조회에서 숨기고 접수 제한은 유지한다")
    void deleteFeedback_hidesFromOwner_preservesSubmissionLimit() throws Exception {
        // Given
        for (int index = 0; index < 4; index++) {
            insertFeedback(UUID.randomUUID(), NOW.minusSeconds(180 + index * 60L));
        }

        // When
        jdbcTemplate.queryForList(
                "SELECT id FROM feedbacks WHERE user_id = ?",
                UUID.class,
                userId
        )
                .forEach(id -> commandService.deleteFeedback(id, ADMIN_ID));

        // Then
        String token = "Bearer "
                + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        mockMvc.perform(get("/api/v1/feedbacks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feedbacks").isEmpty());
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("개인 문의 member@example.com"));
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("userId", userId.toString())
                .header(HttpHeaders.AUTHORIZATION, adminToken())).andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(5));
        assertThatThrownBy(() -> feedbackService.submit(userId, "추가 문의"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("최근 24시간에 최대 5건");
    }

    @Test
    @DisplayName("관리자 반복 삭제는 최초 시각과 감사 한 건을 유지한다")
    void deleteFeedback_repeated_preservesFirstDeletionAndAudit() throws Exception {
        // Given
        commandService.deleteFeedback(feedbackId, ADMIN_ID);
        Instant first = deletedAt();
        given(clock.instant()).willReturn(NOW.plusSeconds(60));

        // When & Then
        mockMvc.perform(delete("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken())).andExpect(status().isNoContent());
        assertThat(deletedAt()).isEqualTo(first);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자가 먼저 삭제한 문의는 관리자 삭제 감사 기록을 새로 만들지 않는다")
    void deleteFeedback_deletedByOwner_preservesStateWithoutAudit() {
        // Given
        feedbackService.deleteFeedback(userId, feedbackId);
        Instant first = deletedAt();

        // When
        commandService.deleteFeedback(feedbackId, ADMIN_ID);

        // Then
        assertThat(deletedAt()).isEqualTo(first);
        assertThat(auditCount()).isZero();
    }

    @Test
    @DisplayName("없는 문의의 관리자 삭제는 기존 오류 형식으로 404를 반환한다")
    void deleteFeedback_missingFeedback_returnsNotFound() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/admin/feedbacks/{feedbackId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, adminToken())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("문의를 찾을 수 없습니다."));
        assertThat(deletedAt()).isNull();
        assertThat(auditCount()).isZero();
    }

    @Test
    @DisplayName("감사 관리자 FK 저장 실패는 문의 삭제도 롤백한다")
    void deleteFeedback_auditFailure_rollsBackDeletion() {
        // When & Then
        assertThatThrownBy(() -> commandService.deleteFeedback(feedbackId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(deletedAt()).isNull();
        assertThat(auditCount()).isZero();
        assertThat(feedbackService.getFeedback(userId, feedbackId).content())
                .isEqualTo("개인 문의 member@example.com");
    }

    @Test
    @DisplayName("DB도 문의 삭제 행위와 다른 대상 유형의 조합을 거절한다")
    void deleteFeedback_invalidAuditTarget_isRejectedByDatabase() {
        // Given
        commandService.deleteFeedback(feedbackId, ADMIN_ID);

        // When & Then
        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                        INSERT INTO admin_audit_logs
                            (actor_admin_id, action, target_type, target_id, before_state, after_state, occurred_at, request_id)
                        SELECT actor_admin_id, action, 'POST', target_id, before_state, after_state, occurred_at, request_id
                        FROM admin_audit_logs WHERE target_id = ?
                        """,
                feedbackId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 관리자 삭제는 문의 잠금으로 직렬화하여 감사 한 건만 남긴다")
    void deleteFeedback_concurrentAdmins_preservesOneAudit() throws Exception {
        // When
        runConcurrentDeletion(() -> commandService.deleteFeedback(feedbackId, ADMIN_ID),
                () -> commandService.deleteFeedback(feedbackId, ADMIN_ID));

        // Then
        assertThat(deletedAt()).isEqualTo(NOW.truncatedTo(ChronoUnit.MICROS));
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 삭제와 경쟁한 관리자는 삭제 상태를 다시 읽고 감사 기록을 만들지 않는다")
    void deleteFeedback_concurrentOwnerFirst_doesNotCreateAudit() throws Exception {
        // When
        runConcurrentDeletion(() -> feedbackService.deleteFeedback(userId, feedbackId),
                () -> commandService.deleteFeedback(feedbackId, ADMIN_ID));

        // Then
        assertThat(deletedAt()).isNotNull();
        assertThat(auditCount()).isZero();
    }

    private void runConcurrentDeletion(Runnable firstDeletion, Runnable secondDeletion)
            throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                feedbackRepository.findByIdForUpdate(feedbackId).orElseThrow();
                locked.countDown();
                await(release);
                firstDeletion.run();
            }));
            try {
                await(locked);
                var second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    secondStarted.countDown();
                    secondDeletion.run();
                }));
                await(secondStarted);
                assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                release.countDown();
                first.get(10, TimeUnit.SECONDS);
                second.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void insertFeedback(UUID id, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at) VALUES (?, ?, ?, ?)",
                id,
                userId,
                "개인 문의 member@example.com",
                Timestamp.from(createdAt)
        );
    }

    private Instant deletedAt() {
        OffsetDateTime value = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM feedbacks WHERE id = ?",
                OffsetDateTime.class,
                feedbackId
        );
        return value == null ? null : value.toInstant();
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_type = 'FEEDBACK'",
                Integer.class);
    }

    private String adminToken() {
        return "Bearer " + accessTokenProvider.issue(ADMIN_ID, AccessTokenScope.ADMIN).value();
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
