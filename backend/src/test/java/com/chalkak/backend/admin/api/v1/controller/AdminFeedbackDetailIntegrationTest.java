package com.chalkak.backend.admin.api.v1.controller;

import static org.hamcrest.Matchers.nullValue;
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
class AdminFeedbackDetailIntegrationTest extends IntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-09-16T01:00:00Z");
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
        jdbcTemplate.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                userId,
                userId + "@chalkak.test",
                "signatures/" + userId
        );
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at) VALUES (?, ?, '관리자 상세 문의', ?)",
                feedbackId,
                userId,
                Timestamp.from(CREATED_AT)
        );
    }

    @Test
    @DisplayName("실제 관리자 인증으로 문의 상세를 조회한다")
    void getFeedback_adminToken_returnsDetail() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.content").value("관리자 상세 문의"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.deletedAt").value(nullValue()))
                .andExpect(jsonPath("$.answerContent").value(nullValue()))
                .andExpect(jsonPath("$.answeredAt").value(nullValue()))
                .andExpect(jsonPath("$.author.userId").value(userId.toString()))
                .andExpect(jsonPath("$.author.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("없는 문의의 관리자 상세는 기존 오류 형식으로 404를 반환한다")
    void getFeedback_missingFeedback_returnsNotFound() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("문의를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("관리자가 조회할 수 있는 삭제 문의도 작성자의 본인 상세에서는 숨긴다")
    void getFeedback_deletedFeedback_isVisibleOnlyToAdmin() throws Exception {
        // Given
        jdbcTemplate.update(
                """
                        UPDATE feedbacks SET deleted_at = ?, answer_content = '관리자 답변',
                            answered_at = TIMESTAMPTZ '2026-11-04 01:00:00.123456+00' WHERE id = ?
                        """,
                Timestamp.from(DELETED_AT),
                feedbackId
        );

        // When & Then
        mockMvc.perform(get("/api/v1/admin/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answerContent").value("관리자 답변"))
                .andExpect(jsonPath("$.answeredAt").value("2026-11-04T01:00:00.123456Z"));
        mockMvc.perform(get("/api/v1/admin/feedbacks").param("userId", userId.toString())
                .header(HttpHeaders.AUTHORIZATION, adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].feedbackId").value(feedbackId.toString()))
                .andExpect(jsonPath("$.feedbacks[0].answerContent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.feedbacks[0].answeredAt").doesNotHaveJsonPath());
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, memberToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.answerContent").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.answeredAt").doesNotHaveJsonPath());
    }

    private String adminToken() {
        return "Bearer "
                + accessTokenProvider.issue(UUID.randomUUID(), AccessTokenScope.ADMIN).value();
    }

    private String memberToken() {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
