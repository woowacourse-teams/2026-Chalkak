package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.service.feedback.AdminFeedbackCommandService;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.notification.repository.NotificationRepository;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminFeedbackAnswerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private JwtAccessTokenProvider tokenProvider;
    @Autowired
    private AdminFeedbackCommandService commandService;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID adminId;
    private UUID userId;
    private UUID feedbackId;

    @BeforeEach
    void setUp() {
        adminId = UUID.randomUUID();
        userId = UUID.randomUUID();
        feedbackId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO admins (id, username, password) VALUES (?, ?, 'test-password')",
                adminId,
                "answer-" + adminId
        );
        jdbc.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                userId,
                userId + "@chalkak.test",
                "signatures/" + userId
        );
        jdbc.update(
                "INSERT INTO feedbacks (id, user_id, content) VALUES (?, ?, '비공개 문의')",
                feedbackId,
                userId
        );
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", adminId);
        jdbc.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM feedbacks WHERE id = ?", feedbackId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        jdbc.update("DELETE FROM admins WHERE id = ?", adminId);
    }

    @Test
    @DisplayName("정지 회원 문의에도 답변·감사·알림을 한 번 저장한다")
    void answerFeedback_bannedAuthor_savesAnswerAuditAndNotificationOnce() throws Exception {
        // Given
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", userId);

        // When
        String answer = "비공개 답변";
        answer(answer, false).andExpect(status().isNoContent());

        // Then
        assertThat(storedAnswer()).isEqualTo(answer);
        assertThat(jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM notifications
                        WHERE user_id = ? AND source_id = ? AND source_type = 'FEEDBACK'
                          AND type = 'FEEDBACK_ANSWERED' AND sqs_publish_status = 'PENDING'
                        """,
                Long.class,
                userId,
                feedbackId
        )).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM admin_audit_logs a JOIN feedbacks f ON a.target_id = f.id
                        JOIN notifications n ON n.source_id = f.id AND n.event_key = a.id
                        WHERE a.actor_admin_id = ? AND f.id = ? AND a.action = 'FEEDBACK_ANSWERED'
                          AND a.before_state = '{"answeredAt":null}'::jsonb
                          AND a.after_state = jsonb_build_object('answeredAt', a.after_state->>'answeredAt')
                          AND (a.after_state->>'answeredAt')::timestamptz = f.answered_at
                        """,
                Long.class,
                adminId,
                feedbackId
        )).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("삭제된 문의 또는 탈퇴 회원의 문의에는 답변과 알림을 만들지 않는다")
    void answerFeedback_deletedFeedbackOrWithdrawnAuthor_rejects(boolean deletedFeedback)
            throws Exception {
        // Given
        if (deletedFeedback) {
            jdbc.update("UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                    feedbackId);
        } else {
            jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
        }

        // When & Then
        answer("답변").andExpect(status().isBadRequest());
        assertNoAnswerOrNotification();
    }

    @Test
    @DisplayName("감사 저장이 실패하면 답변을 롤백하고 알림을 남기지 않는다")
    void answerFeedback_auditFailure_rollsBackAnswerAndNotification() {
        // When & Then
        assertThatThrownBy(() -> commandService.answerFeedback(
                feedbackId,
                UUID.randomUUID(),
                "답변",
                false
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertNoAnswerOrNotification();
    }

    @Test
    @DisplayName("알림 저장이 실패하면 답변과 먼저 저장한 감사도 롤백한다")
    void answerFeedback_notificationFailure_rollsBackAnswerAndAudit() {
        // Given
        doThrow(new DataIntegrityViolationException("알림 저장 실패"))
                .when(notificationRepository).save(any());

        // When & Then
        assertThatThrownBy(() -> commandService.answerFeedback(
                feedbackId,
                adminId,
                "답변",
                false
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertNoAnswerOrNotification();
    }

    @Test
    @DisplayName("동시 답변 요청은 한 건만 성공하고 알림과 감사도 한 번만 남긴다")
    void answerFeedback_concurrentRequests_savesOnlyOnce() throws Exception {
        // Given
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CompletableFuture<Integer> firstPid = new CompletableFuture<>();
        CompletableFuture<Integer> secondPid = new CompletableFuture<>();
        CountDownLatch releaseFirst = new CountDownLatch(1);

        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transaction.executeWithoutResult(status -> {
                feedbackRepository.findByIdForUpdate(feedbackId).orElseThrow();
                firstPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                awaitRelease(releaseFirst);
                commandService.answerFeedback(
                        feedbackId,
                        adminId,
                        "첫 답변",
                        false
                );
            }));
            try {
                int lockOwner = firstPid.get(5, TimeUnit.SECONDS);
                var second = executor.submit(() -> transaction.executeWithoutResult(status -> {
                    secondPid.complete(
                            jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    commandService.answerFeedback(
                            feedbackId,
                            adminId,
                            "다른 답변",
                            false
                    );
                }));
                int waiter = secondPid.get(5, TimeUnit.SECONDS);
                await().atMost(Duration.ofSeconds(5)).until(() -> Boolean.TRUE.equals(
                        jdbc.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))",
                                Boolean.class,
                                lockOwner,
                                waiter
                        )));
                releaseFirst.countDown();
                first.get(10, TimeUnit.SECONDS);

                // Then
                assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(BusinessException.class)
                        .hasRootCauseMessage("이미 답변한 문의입니다.");
            } finally {
                releaseFirst.countDown();
            }
        }
        assertThat(storedAnswer()).isEqualTo("첫 답변");
        assertThat(notificationCount()).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("관리자·사용자 문의 삭제는 알림 행을 지우고 관리자에게 문의 원문을 보존한다")
    void deleteFeedback_answeredFeedback_deletesNotification(boolean adminDelete) throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());
        assertThat(notificationCount()).isEqualTo(1L);
        String memberToken = "Bearer "
                + tokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When
        String path = adminDelete ? "/api/v1/admin/feedbacks/{id}" : "/api/v1/feedbacks/{id}";
        mockMvc.perform(delete(path, feedbackId).header(
                HttpHeaders.AUTHORIZATION, adminDelete ? adminToken() : memberToken))
                .andExpect(status().isNoContent());

        // Then
        assertThat(notificationCount()).isZero();
        mockMvc.perform(get("/api/v1/admin/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deletedAt").isNotEmpty())
                .andExpect(jsonPath("$.content").value("비공개 문의"))
                .andExpect(jsonPath("$.answerContent").value("비공개 답변"));
    }

    @Test
    @DisplayName("문의 삭제 감사 저장이 실패하면 문의와 답변 알림을 모두 보존한다")
    void deleteFeedback_auditFailure_preservesFeedbackAndNotification() throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());

        // When & Then
        assertThatThrownBy(() -> commandService.deleteFeedback(feedbackId, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(notificationCount()).isEqualTo(1L);
        String memberToken = "Bearer "
                + tokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        mockMvc.perform(get("/api/v1/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerContent").value("비공개 답변"));
    }

    @Test
    @DisplayName("같은 답변을 반복 삭제하면 원문은 보존하고 알림과 감사 상태는 한 번만 변경한다")
    void deleteFeedbackAnswer_sameAnswerTwice_hidesAnswerAndDeletesNotification() throws Exception {
        // Given
        mockMvc.perform(get("/api/v1/admin/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerState").value("UNANSWERED"));
        answer("비공개 답변").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/admin/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerState").value("ANSWERED"));
        Instant answeredAt = savedAnsweredAt();
        String answerVersion = currentAnswerVersion();
        String memberToken = "Bearer "
                + tokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When
        deleteAnswer(answerVersion).andExpect(status().isNoContent());
        deleteAnswer(answerVersion).andExpect(status().isNoContent());

        // Then
        assertThat(notificationCount()).isZero();
        assertThat(storedAnswer()).isEqualTo("비공개 답변");
        assertThat(savedAnsweredAt()).isEqualTo(answeredAt);
        assertThat(jdbc.queryForObject(
                """
                        SELECT COUNT(*) FROM admin_audit_logs a JOIN feedbacks f ON a.target_id = f.id
                        WHERE f.id = ? AND a.action = 'FEEDBACK_ANSWER_DELETED'
                          AND a.before_state = jsonb_build_object('answeredAt',
                              a.before_state->>'answeredAt', 'answerDeletedAt', null)
                          AND a.after_state = jsonb_build_object('answeredAt',
                              a.after_state->>'answeredAt', 'answerDeletedAt', a.after_state->>'answerDeletedAt')
                          AND (a.after_state->>'answeredAt')::timestamptz = f.answered_at
                          AND (a.after_state->>'answerDeletedAt')::timestamptz = f.answer_deleted_at
                        """,
                Long.class,
                feedbackId
        )).isEqualTo(1L);
        mockMvc.perform(get("/api/v1/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("비공개 문의"))
                .andExpect(jsonPath("$.answerContent").value(nullValue()))
                .andExpect(jsonPath("$.answeredAt").value(nullValue()));
        mockMvc.perform(get("/api/v1/admin/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerContent").value(nullValue()))
                .andExpect(jsonPath("$.answeredAt").value(nullValue()))
                .andExpect(jsonPath("$.answerState").value("DELETED"));
    }

    @Test
    @DisplayName("이전 답변 시각으로 삭제하면 현재 답변과 알림은 보존한다")
    void deleteFeedbackAnswer_staleAnsweredAt_preservesCurrentAnswer() throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());

        // When & Then
        deleteAnswer(savedAnsweredAt().minusSeconds(1)).andExpect(status().isBadRequest());
        assertThat(notificationCount()).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(1L);
        assertThat(savedAnswerDeletedAt()).isNull();
    }

    @Test
    @DisplayName("답변 삭제 감사가 실패하면 삭제 시각과 알림 삭제를 함께 롤백한다")
    void deleteFeedbackAnswer_auditFailure_rollsBackDeletionAndNotification() throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());
        Instant answeredAt = savedAnsweredAt();

        // When & Then
        assertThatThrownBy(() -> commandService.deleteFeedbackAnswer(
                feedbackId,
                UUID.randomUUID(),
                Feedback.createAnswerVersion(answeredAt)
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(notificationCount()).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(1L);
        assertThat(savedAnswerDeletedAt()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {true, false})
    @DisplayName("재답변은 알림을 새로 저장하고 true인 경우만 푸시 대기에 넣는다")
    void answerFeedback_deletedAnswer_createsNewNotificationWithSelectedPush(Boolean sendPush)
            throws Exception {
        // Given
        answer("이전 답변").andExpect(status().isNoContent());
        Instant previousAnsweredAt = savedAnsweredAt();
        UUID previousEventKey = jdbc.queryForObject(
                "SELECT event_key FROM notifications WHERE source_id = ?",
                UUID.class,
                feedbackId
        );
        deleteAnswer(previousAnsweredAt).andExpect(status().isNoContent());

        // When
        answer("새 답변", sendPush).andExpect(status().isNoContent());
        deleteAnswer(previousAnsweredAt).andExpect(status().isBadRequest());

        // Then
        assertThat(storedAnswer()).isEqualTo("새 답변");
        assertThat(savedAnsweredAt()).isAfter(previousAnsweredAt);
        assertThat(savedAnswerDeletedAt()).isNull();
        assertThat(notificationCount()).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(3L);
        assertThat(jdbc.queryForMap(
                """
                        SELECT n.event_key, n.sqs_publish_status, a.before_state->>'answeredAt' AS previous_answered_at
                        FROM notifications n JOIN admin_audit_logs a ON a.id = n.event_key
                        WHERE n.source_id = ? AND a.action = 'FEEDBACK_ANSWERED'
                        """,
                feedbackId
        )).containsEntry("sqs_publish_status",
                Boolean.TRUE.equals(sendPush) ? "PENDING" : "NOT_REQUIRED")
                .containsEntry("previous_answered_at", previousAnsweredAt.toString())
                .doesNotContainEntry("event_key", previousEventKey);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("답변 삭제 뒤 문의가 삭제됐거나 작성자가 탈퇴하면 재답변할 수 없다")
    void answerFeedback_deletedInquiryOrWithdrawnAuthor_rejectsReanswer(boolean deleteInquiry)
            throws Exception {
        // Given
        answer("이전 답변").andExpect(status().isNoContent());
        deleteAnswer(savedAnsweredAt()).andExpect(status().isNoContent());
        if (deleteInquiry) {
            commandService.deleteFeedback(feedbackId, adminId);
        } else {
            jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);
        }

        // When & Then
        answer("새 답변", true).andExpect(status().isBadRequest());
        assertThat(storedAnswer()).isEqualTo("이전 답변");
        assertThat(notificationCount()).isZero();
    }

    private String currentAnswerVersion() throws Exception {
        var response = mockMvc.perform(get("/api/v1/admin/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerVersion").isString())
                .andReturn().getResponse();
        return objectMapper.readTree(response.getContentAsByteArray()).get("answerVersion")
                .asString();
    }

    private ResultActions deleteAnswer(Instant answeredAt) throws Exception {
        return deleteAnswer(Feedback.createAnswerVersion(answeredAt));
    }

    private ResultActions deleteAnswer(String answerVersion) throws Exception {
        return mockMvc.perform(delete("/api/v1/admin/feedbacks/{id}/answer", feedbackId)
                .param("answerVersion", answerVersion)
                .header(HttpHeaders.AUTHORIZATION, adminToken()));
    }

    private Instant savedAnsweredAt() {
        return jdbc.queryForObject(
                "SELECT answered_at FROM feedbacks WHERE id = ?",
                OffsetDateTime.class,
                feedbackId
        ).toInstant();
    }

    private OffsetDateTime savedAnswerDeletedAt() {
        return jdbc.queryForObject(
                "SELECT answer_deleted_at FROM feedbacks WHERE id = ?",
                OffsetDateTime.class,
                feedbackId
        );
    }

    private void awaitRelease(CountDownLatch release) {
        try {
            assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private String adminToken() {
        return "Bearer " + tokenProvider.issue(adminId, AccessTokenScope.ADMIN).value();
    }

    private ResultActions answer(String content) throws Exception {
        return answer(content, null);
    }

    private ResultActions answer(String content, Boolean sendPush) throws Exception {
        String push = sendPush == null ? "" : ",\"sendPush\":" + sendPush;
        return mockMvc.perform(post("/api/v1/admin/feedbacks/{feedbackId}/answer", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerContent\":\"" + content + "\"" + push + "}"));
    }

    private String storedAnswer() {
        return jdbc.queryForObject(
                "SELECT answer_content FROM feedbacks WHERE id = ?",
                String.class,
                feedbackId
        );
    }

    private long notificationCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                Long.class,
                userId
        );
    }

    private long auditCount() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Long.class,
                feedbackId
        );
    }

    private void assertNoAnswerOrNotification() {
        assertThat(storedAnswer()).isNull();
        assertThat(notificationCount()).isZero();
        assertThat(auditCount()).isZero();
    }
}
