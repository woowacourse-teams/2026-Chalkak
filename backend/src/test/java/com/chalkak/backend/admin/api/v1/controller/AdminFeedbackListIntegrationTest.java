package com.chalkak.backend.admin.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.chalkak.backend.admin.service.feedback.AdminFeedbackAnswerState.ANSWERED;
import static com.chalkak.backend.admin.service.feedback.AdminFeedbackAnswerState.DELETED;
import static com.chalkak.backend.admin.service.feedback.AdminFeedbackAnswerState.UNANSWERED;

import com.chalkak.backend.admin.service.feedback.AdminFeedbackAnswerState;
import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
@TestPropertySource(properties = "chalkak.admin.authentication.development-bypass-enabled=false")
class AdminFeedbackListIntegrationTest extends IntegrationTestSupport {

    private static final Instant DELETED_AT = Instant.parse("2026-11-03T09:00:00Z");
    private static final Instant CREATED_AT = DELETED_AT.minusSeconds(60);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    private UUID userId;
    private UUID otherUserId;
    private UUID feedbackId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        feedbackId = UUID.randomUUID();
        insertUser(userId, "BANNED");
        otherUserId = UUID.randomUUID();
        insertUser(otherUserId, "ACTIVE");
        jdbcTemplate.update(
                """
                        INSERT INTO feedbacks (id, user_id, content, created_at, deleted_at)
                        VALUES (?, ?, '삭제된 내 문의', ?, ?), (?, ?, '다른 사용자의 문의', ?, NULL)
                        """,
                feedbackId,
                userId,
                Timestamp.from(CREATED_AT.plusSeconds(10)),
                Timestamp.from(DELETED_AT),
                UUID.randomUUID(),
                otherUserId,
                Timestamp.from(DELETED_AT)
        );
    }

    @Test
    @DisplayName("실제 관리자 인증으로 사용자별 삭제 문의와 작성자 상태를 조회한다")
    void getFeedbacks_adminToken_returnsOnlySelectedUsersDeletedFeedback() throws Exception {
        // Given
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(1))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].content").value("삭제된 내 문의"))
                .andExpect(jsonPath("$.feedbacks[0].deletedAt").value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.feedbacks[0].author.userId").value(userId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].author.status").value("BANNED"));
    }

    @Test
    @DisplayName("관리자 목록은 현재 답변 상태만 노출한다")
    void getFeedbacks_withAnswerHistory_returnsStatesWithoutAnswerDetails() throws Exception {
        // Given
        UUID answeredId = insertFeedback(
                userId,
                "답변 완료",
                CREATED_AT,
                false,
                ANSWERED
        );
        UUID answerDeletedId = insertFeedback(
                userId,
                "답변 삭제",
                CREATED_AT.plusSeconds(1),
                false,
                DELETED
        );
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .param("sort", "createdAtAsc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(answeredId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].answerState").value("ANSWERED"))
                .andExpect(jsonPath("$.feedbacks[1].feedbackId").value(answerDeletedId.toString()))
                .andExpect(jsonPath("$.feedbacks[1].answerState").value("DELETED"))
                .andExpect(jsonPath("$.feedbacks[2].answerState").value("UNANSWERED"))
                .andExpect(jsonPath("$.feedbacks[0].answerContent").doesNotExist())
                .andExpect(jsonPath("$.feedbacks[0].answeredAt").doesNotExist())
                .andExpect(jsonPath("$.feedbacks[0].answerVersion").doesNotExist());
    }

    @Test
    @DisplayName("사용자와 답변 필요 조건을 먼저 적용한 뒤 페이지를 나눈다")
    void getFeedbacks_needsAnswer_filtersBeforePagination() throws Exception {
        // Given
        UUID unanswered = insertFeedback(
                userId,
                "최초 미답변",
                CREATED_AT,
                false,
                UNANSWERED
        );
        UUID answerDeleted = insertFeedback(
                userId,
                "재답변 필요",
                CREATED_AT.plusSeconds(1),
                false,
                DELETED
        );
        insertFeedback(
                userId,
                "이미 답변함",
                CREATED_AT.plusSeconds(2),
                false,
                ANSWERED
        );
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .param("inquiryStatus", "NEEDS_ANSWER")
                .param("pageSize", "1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(answerDeleted.toString()))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .param("inquiryStatus", "NEEDS_ANSWER")
                .param("pageSize", "1")
                .param("page", "2")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(unanswered.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("현재 답변이 있는 문의와 문의 자체가 삭제된 기록을 구분한다")
    void getFeedbacks_answeredAndDeletedFilters_separateInquiryDeletion() throws Exception {
        // Given
        UUID answered = insertFeedback(
                userId,
                "답변이 남아 있는 문의",
                CREATED_AT,
                false,
                ANSWERED
        );
        UUID deleted = insertFeedback(
                userId,
                "답변이 남은 삭제 문의",
                CREATED_AT.plusSeconds(1),
                true,
                ANSWERED
        );
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("inquiryStatus", "ANSWERED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(1))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(answered.toString()));
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("inquiryStatus", "DELETED")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(2))
                .andExpect(jsonPath("$.feedbacks[1].feedbackId").value(deleted.toString()))
                .andExpect(jsonPath("$.feedbacks[1].answerState").value("ANSWERED"));
    }

    @Test
    @DisplayName("답변 필요에서 탈퇴 회원을 제외하고 차단 회원 조건과 조합한다")
    void getFeedbacks_bannedAuthorsOnly_combinesWithNeedsAnswer() throws Exception {
        // Given
        UUID withdrawnId = UUID.randomUUID();
        insertUser(withdrawnId, "BANNED");
        jdbcTemplate.update(
                "UPDATE users SET deleted_at = ? WHERE id = ?",
                Timestamp.from(DELETED_AT),
                withdrawnId
        );
        insertFeedback(
                withdrawnId,
                "차단 뒤 탈퇴한 회원의 문의",
                CREATED_AT.plusSeconds(4),
                false,
                UNANSWERED
        );
        UUID bannedFeedback = insertFeedback(
                userId,
                "차단 회원 문의",
                CREATED_AT.plusSeconds(3),
                false,
                UNANSWERED
        );
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("inquiryStatus", "NEEDS_ANSWER")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(2));
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("inquiryStatus", "NEEDS_ANSWER")
                .param("bannedAuthorOnly", "true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(1))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(bannedFeedback.toString()));
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("bannedAuthorOnly", "true")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(2))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[1].feedbackId").value(bannedFeedback.toString()));
    }

    private UUID insertFeedback(
            UUID authorId,
            String content,
            Instant createdAt,
            boolean deleted,
            AdminFeedbackAnswerState answerState
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO feedbacks
                            (id, user_id, content, created_at, deleted_at, answer_content, answered_at, answer_deleted_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                authorId,
                content,
                Timestamp.from(createdAt),
                deleted ? Timestamp.from(createdAt.plusSeconds(3)) : null,
                answerState != UNANSWERED ? "답변 원문" : null,
                answerState != UNANSWERED ? Timestamp.from(createdAt.plusSeconds(1)) : null,
                answerState == DELETED ? Timestamp.from(createdAt.plusSeconds(2)) : null
        );
        return id;
    }

    private void insertUser(UUID id, String status) {
        jdbcTemplate.update(
                """
                                INSERT INTO users (id, email, status, signature_original_storage_key)
                        VALUES (?, ?, CAST(? AS user_status), ?)
                                """,
                id,
                id + "@chalkak.test",
                status,
                "signatures/" + id
        );
    }
}
