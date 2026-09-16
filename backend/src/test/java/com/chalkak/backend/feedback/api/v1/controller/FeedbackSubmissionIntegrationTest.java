package com.chalkak.backend.feedback.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.access.JwtAccessTokenProvider;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제출 성공 경로를 실제 스택으로 한 번 통과시킨다.
 *
 * <p>{@code FeedbackService}는 flush 없이 저장 직후 식별자와 접수 시각을 읽어 응답에 싣는다.
 * 컨트롤러 테스트는 서비스를 목으로 대체하고 레포지토리 테스트는 명시적으로 flush하므로, 그
 * 조합이 실제로 값을 채우는지는 어느 쪽에서도 드러나지 않는다. 여기서만 드러난다.
 */
@Transactional
@AutoConfigureMockMvc
class FeedbackSubmissionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status,
                    signature_original_storage_key, signature_thumbnail_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'author@chalkak.test', 'ACTIVE',
                    'chalkak/signatures/original/author.png',
                    'chalkak/signatures/thumbnail/author.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, userId);
        token = "Bearer " + accessTokenProvider.issue(userId).value();
    }

    @Test
    @DisplayName("제출 응답은 실제로 채워진 식별자와 접수 시각을 담는다")
    void submitFeedback_validRequest_returnsPersistedIdentifierAndCreatedAt() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "  사진 업로드가 느려요.  "
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.feedbackId").isNotEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT content FROM feedbacks WHERE user_id = ?", userId);
        assertThat(stored.get("content")).isEqualTo("사진 업로드가 느려요.");
    }

    @Test
    @DisplayName("TEXT 컬럼은 허용 상한인 보조 평면 문자 1000자를 그대로 보관한다")
    void submitFeedback_maxLengthSupplementaryCharacters_areStoredIntact() throws Exception {
        String content = "😀".repeat(Feedback.MAX_CONTENT_LENGTH);

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"}"))
                .andExpect(status().isCreated());

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT content FROM feedbacks WHERE user_id = ?", userId);
        assertThat(stored.get("content")).isEqualTo(content);
    }
}
