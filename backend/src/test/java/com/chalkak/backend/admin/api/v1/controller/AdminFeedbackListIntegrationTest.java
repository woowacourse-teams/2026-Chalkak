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
    private UUID feedbackId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        feedbackId = UUID.randomUUID();
        insertUser(userId, "BANNED");
        UUID otherUserId = UUID.randomUUID();
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
    @DisplayName("관리자 목록에서 해당 사용자의 삭제 문의와 답변 상태를 조회한다")
    void getFeedbacks_inquiryDeleted_returnsOnlySelectedUsersDeletedInquiries() throws Exception {
        // Given
        UUID answeredId = insertFeedback(
                userId,
                "답변이 남은 삭제 문의",
                CREATED_AT,
                true,
                ANSWERED
        );
        UUID answerDeletedId = insertFeedback(
                userId,
                "답변 삭제 후 삭제된 문의",
                CREATED_AT.plusSeconds(1),
                true,
                DELETED
        );
        insertFeedback(
                userId,
                "삭제되지 않은 문의",
                CREATED_AT.plusSeconds(2),
                false,
                ANSWERED
        );
        String token = accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .param("inquiryStatus", "INQUIRY_DELETED")
                .param("sort", "createdAtAsc")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(3))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(answeredId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].answerState").value("ANSWERED"))
                .andExpect(jsonPath("$.feedbacks[1].feedbackId").value(answerDeletedId.toString()))
                .andExpect(jsonPath("$.feedbacks[1].answerState").value("DELETED"))
                .andExpect(jsonPath("$.feedbacks[2].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[2].content").value("삭제된 내 문의"))
                .andExpect(jsonPath("$.feedbacks[2].deletedAt").value(DELETED_AT.toString()))
                .andExpect(jsonPath("$.feedbacks[2].author.userId").value(userId.toString()))
                .andExpect(jsonPath("$.feedbacks[2].author.status").value("BANNED"))
                .andExpect(jsonPath("$.feedbacks[2].answerState").value("UNANSWERED"));
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
