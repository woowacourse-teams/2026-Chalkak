package com.chalkak.backend.feedback.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class FeedbackDetailIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "BANNED"})
    @DisplayName("정상·정지 회원은 본인 문의 상세를 조회한다")
    void getFeedback_activeOrBannedUser_returnsOwnDetail(UserStatus userStatus) throws Exception {
        // Given
        UUID userId = createUser(userStatus);
        UUID feedbackId = createFeedback(userId);

        // When & Then
        getFeedback(userId, feedbackId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.content").value("비공개 문의 원문"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.answerContent").value(nullValue()))
                .andExpect(jsonPath("$.answeredAt").value(nullValue()));
    }

    @Test
    @DisplayName("본인 상세에는 저장된 답변을 표시하고 목록에는 노출하지 않는다")
    void getFeedback_answeredFeedback_returnsAnswerOnlyInDetail() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        addAnswer(feedbackId);

        // When & Then
        getFeedback(userId, feedbackId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerContent").value("비공개 답변"))
                .andExpect(jsonPath("$.answeredAt").value("2026-11-04T01:00:00.123456Z"));
        mockMvc.perform(
                get("/api/v1/feedbacks").header(HttpHeaders.AUTHORIZATION, tokenFor(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].answerContent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.feedbacks[0].answeredAt").doesNotHaveJsonPath());
    }

    @Test
    @DisplayName("타인 문의는 404로 응답하며 원문을 노출하지 않는다")
    void getFeedback_otherUserFeedback_returnsNotFound() throws Exception {
        // Given
        UUID authorId = createUser(UserStatus.ACTIVE);
        UUID viewerId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(authorId);
        addAnswer(feedbackId);

        // When & Then
        expectNotFound(getFeedback(viewerId, feedbackId));
    }

    @Test
    @DisplayName("삭제된 문의는 본인에게도 404로 응답하며 원문을 노출하지 않는다")
    void getFeedback_deletedFeedback_returnsNotFound() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        jdbcTemplate.update("UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                feedbackId);

        // When & Then
        expectNotFound(getFeedback(userId, feedbackId));
    }

    @Test
    @DisplayName("없는 문의도 타인·삭제 문의와 같은 404 응답을 반환한다")
    void getFeedback_missingFeedback_returnsNotFound() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);

        // When & Then
        expectNotFound(getFeedback(userId, UUID.randomUUID()));
    }

    @Test
    @DisplayName("탈퇴한 회원은 남은 토큰으로 본인 문의 상세를 조회할 수 없다")
    void getFeedback_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        String token = tokenFor(userId);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증하지 않은 요청은 문의 상세를 조회할 수 없다")
    void getFeedback_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    private UUID createUser(UserStatus userStatus) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, status, signature_original_storage_key) VALUES (?, ?, CAST(? AS user_status), ?)",
                userId,
                userId + "@chalkak.test",
                userStatus.name(),
                "signatures/" + userId
        );
        return userId;
    }

    private UUID createFeedback(UUID userId) {
        UUID feedbackId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content) VALUES (?, ?, '비공개 문의 원문')",
                feedbackId,
                userId
        );
        return feedbackId;
    }

    private void addAnswer(UUID feedbackId) {
        jdbcTemplate.update(
                "UPDATE feedbacks SET answer_content = '비공개 답변', answered_at = TIMESTAMPTZ '2026-11-04 01:00:00.123456+00' WHERE id = ?",
                feedbackId
        );
    }

    private ResultActions getFeedback(UUID userId, UUID feedbackId) throws Exception {
        return mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, tokenFor(userId)));
    }

    private void expectNotFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("문의를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.answerContent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.answeredAt").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.content").doesNotExist())
                .andExpect(jsonPath("$.feedbackId").doesNotExist());
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
