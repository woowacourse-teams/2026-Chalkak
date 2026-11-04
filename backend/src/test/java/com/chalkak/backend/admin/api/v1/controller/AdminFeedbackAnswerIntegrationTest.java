package com.chalkak.backend.admin.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.admin.service.feedback.AdminFeedbackCommandService;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.notification.infrastructure.infra.NotificationCleanupScheduler;
import com.chalkak.backend.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    private NotificationCleanupScheduler notificationCleanupScheduler;

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
        answer(answer).andExpect(status().isNoContent());

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
                "답변"
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
                "답변"
        )).isInstanceOf(DataIntegrityViolationException.class);
        assertNoAnswerOrNotification();
    }

    @Test
    @DisplayName("동시 답변 요청은 한 건만 성공하고 알림과 감사도 한 번만 남긴다")
    void answerFeedback_concurrentRequests_savesOnlyOnce() throws Exception {
        // Given
        var start = new CyclicBarrier(2);

        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return answer("첫 답변").andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                return answer("다른 답변").andReturn().getResponse().getStatus();
            });

            // Then
            assertThat(List.of(first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(204, 400);
        }
        assertThat(storedAnswer()).isIn("첫 답변", "다른 답변");
        assertThat(notificationCount()).isEqualTo(1L);
        assertThat(auditCount()).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("관리자·사용자 문의 삭제는 답변 알림 행을 지우고 발행과 발송 대상에서 제외한다")
    void deleteFeedback_answeredFeedback_deletesNotification(boolean adminDelete) throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());
        UUID notificationId = jdbc.queryForObject(
                "SELECT id FROM notifications WHERE user_id = ?",
                UUID.class,
                userId
        );
        String memberToken = "Bearer "
                + tokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        Instant dueAt = Instant.now().plusSeconds(60);
        assertThat(notificationRepository.findDuePublicationIds(dueAt, 100))
                .contains(notificationId);
        assertThat(notificationRepository.findForPushByIdAndUserId(notificationId, userId))
                .isPresent();
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].type").value("FEEDBACK_ANSWERED"))
                .andExpect(jsonPath("$.notifications[0].sourceType").value("FEEDBACK"))
                .andExpect(jsonPath("$.notifications[0].sourceId").value(feedbackId.toString()));

        // When
        String path = adminDelete ? "/api/v1/admin/feedbacks/{id}" : "/api/v1/feedbacks/{id}";
        mockMvc.perform(delete(path, feedbackId).header(
                HttpHeaders.AUTHORIZATION, adminDelete ? adminToken() : memberToken))
                .andExpect(status().isNoContent());

        // Then
        mockMvc.perform(get("/api/v1/notifications").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications").isEmpty());
        mockMvc.perform(get("/api/v1/notifications/unread-status")
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasUnread").value(false));
        mockMvc.perform(get("/api/v1/notifications/{id}", notificationId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isNotFound());
        assertThat(notificationRepository.findDuePublicationIds(dueAt, 100))
                .doesNotContain(notificationId);
        assertThat(notificationRepository.findForPushByIdAndUserId(notificationId, userId))
                .isEmpty();
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
    @DisplayName("보관 기간이 지난 답변 알림은 문의를 삭제하지 않아도 정리하며 문의와 답변은 보존한다")
    void deleteExpiredNotifications_openFeedback_preservesFeedbackAndAnswer() throws Exception {
        // Given
        answer("비공개 답변").andExpect(status().isNoContent());
        jdbc.update(
                "UPDATE notifications SET created_at = CURRENT_TIMESTAMP - INTERVAL '31 days' WHERE user_id = ?",
                userId
        );

        // When
        notificationCleanupScheduler.deleteExpiredNotifications();

        // Then
        assertThat(notificationCount()).isZero();
        String memberToken = "Bearer "
                + tokenProvider.issueForSession(userId, UUID.randomUUID()).value();
        mockMvc.perform(get("/api/v1/feedbacks/{id}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("비공개 문의"))
                .andExpect(jsonPath("$.answerContent").value("비공개 답변"));
    }

    private String adminToken() {
        return "Bearer " + tokenProvider.issue(adminId, AccessTokenScope.ADMIN).value();
    }

    private ResultActions answer(String content) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/feedbacks/{feedbackId}/answer", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"answerContent\":\"" + content + "\"}"));
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
