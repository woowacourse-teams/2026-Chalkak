package com.chalkak.backend.feedback.api.v1.controller;

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
import org.springframework.transaction.annotation.Transactional;

@Transactional
@AutoConfigureMockMvc
class FeedbackListIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "BANNED"})
    @DisplayName("정상·정지 회원은 요청에 타인 식별자가 있어도 본인 문의만 조회한다")
    void getFeedbacks_activeOrBannedUser_returnsOnlyOwnFeedback(UserStatus userStatus)
            throws Exception {
        // Given
        UUID userId = createUser(userStatus);
        UUID otherUserId = createUser(UserStatus.ACTIVE);
        UUID ownFeedbackId = createFeedback(userId, "내 문의");
        createFeedback(otherUserId, "타인 비공개 문의");
        UUID deletedFeedbackId = createFeedback(userId, "삭제된 문의");
        jdbcTemplate.update(
                "UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                deletedFeedbackId
        );

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, tokenFor(userId))
                .param("userId", otherUserId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks.length()").value(1))
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(ownFeedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].content").value("내 문의"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("탈퇴한 회원은 유효 기간이 남은 토큰으로도 목록을 조회할 수 없다")
    void getFeedbacks_withdrawnUser_returnsUnauthorized() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        String token = tokenFor(userId);
        createFeedback(userId, "탈퇴 전 문의");
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("인증하지 않은 요청은 문의 목록을 조회할 수 없다")
    void getFeedbacks_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/feedbacks"))
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

    private UUID createFeedback(UUID userId, String content) {
        UUID feedbackId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content) VALUES (?, ?, ?)",
                feedbackId,
                userId,
                content
        );
        return feedbackId;
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
