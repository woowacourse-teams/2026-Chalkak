package com.chalkak.backend.admin.api.v1.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.domain.token.AccessTokenScope;
import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
        insertUser(userId);
        UUID otherUserId = UUID.randomUUID();
        insertUser(otherUserId);
        jdbcTemplate.update(
                """
                        INSERT INTO feedbacks (id, user_id, content, deleted_at)
                        VALUES (?, ?, '삭제된 내 문의', ?), (?, ?, '다른 사용자의 문의', NULL)
                        """,
                feedbackId,
                userId,
                Timestamp.from(DELETED_AT),
                UUID.randomUUID(),
                otherUserId
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

    @ParameterizedTest
    @CsvSource({"ACTIVE,false", "BANNED,false", "ACTIVE,true"})
    @DisplayName("정상·정지·탈퇴 회원 토큰으로는 관리자 문의를 조회하지 못한다")
    void getFeedbacks_memberToken_returnsForbidden(String userStatus, boolean withdrawn)
            throws Exception {
        // Given
        jdbcTemplate.update(
                "UPDATE users SET status = CAST(? AS user_status), deleted_at = ? WHERE id = ?",
                userStatus,
                withdrawn ? Timestamp.from(DELETED_AT) : null,
                userId
        );
        String token = accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks")
                .param("userId", userId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("관리자 문의 목록은 인증 없이 조회할 수 없다")
    void getFeedbacks_withoutToken_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("userId", userId.toString()))
                .andExpect(status().isUnauthorized());
    }

    private void insertUser(UUID id) {
        jdbcTemplate.update(
                """
                                INSERT INTO users (id, email, status, signature_original_storage_key)
                        VALUES (?, ?, 'BANNED', ?)
                                """,
                id,
                id + "@chalkak.test",
                "signatures/" + id
        );
    }
}
