package com.chalkak.backend.feedback.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제출 성공 경로를 실제 스택으로 한 번 통과시킨다.
 *
 * <p>
 * {@code FeedbackService}는 flush 없이 저장 직후 식별자와 접수 시각을 읽어 응답에 싣는다. 컨트롤러 테스트는
 * 서비스를 목으로 대체하고 레포지토리 테스트는 명시적으로 flush하므로, 그 조합이 실제로 값을 채우는지는 어느 쪽에서도 드러나지 않는다.
 * 여기서만 드러난다.
 */
@Transactional
@AutoConfigureMockMvc
class FeedbackSubmissionIntegrationTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-01-01T06:00:00.123456Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @MockitoBean
    private Clock clock;

    private UUID userId;
    private String token;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
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
        token = "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
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
                .andExpect(jsonPath("$.createdAt").value(NOW.toString()));

        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT id, created_at, content FROM feedbacks WHERE user_id = ?", userId);
        assertThat(stored.get("id")).isNotNull();
        assertThat(((Timestamp) stored.get("created_at")).toInstant()).isEqualTo(NOW);
        assertThat(stored.get("content")).isEqualTo("사진 업로드가 느려요.");
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-01T06:00:00.123456001Z, 2026-01-01T06:00:00.123456Z",
            "2026-01-01T06:00:00.123456789Z, 2026-01-01T06:00:00.123456Z",
            "2026-01-01T06:00:00.999999999Z, 2026-01-01T06:00:00.999999Z"
    })
    @DisplayName("나노초 Clock으로 접수해도 응답·DB·목록·상세 시각은 같은 마이크로초 값이다")
    void submitFeedback_nanosecondClock_keepsCreatedAtConsistentAcrossReads(
            String clockTime,
            String expectedCreatedAt
    ) throws Exception {
        // Given
        given(clock.instant()).willReturn(Instant.parse(clockTime));

        // When
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"시각 정밀도 문의\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").value(expectedCreatedAt));
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT id, created_at FROM feedbacks WHERE user_id = ?", userId);
        assertThat(((Timestamp) stored.get("created_at")).toInstant())
                .isEqualTo(Instant.parse(expectedCreatedAt));
        mockMvc.perform(get("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks[0].createdAt").value(expectedCreatedAt));
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", stored.get("id"))
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(expectedCreatedAt));
    }

    @ParameterizedTest
    @CsvSource({"59999999000, 400, 1", "60000000000, 201, 2", "60000001000, 201, 2"})
    @DisplayName("저장한 접수 시각 기준으로 1분 직전은 거절하고 경계부터 허용한다")
    void submitFeedback_fixedClock_respectsPersistedMinuteBoundary(
            long elapsedNanos,
            int expectedStatus,
            long expectedCount
    ) throws Exception {
        // Given
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"첫 문의\"}"))
                .andExpect(status().isCreated());
        given(clock.instant()).willReturn(NOW.plusNanos(elapsedNanos));

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"다음 문의\"}"))
                .andExpect(status().is(expectedStatus));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks WHERE user_id = ?",
                Long.class,
                userId
        )).isEqualTo(expectedCount);
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

    @Test
    @DisplayName("잘못된 내용으로 실패한 요청은 다음 정상 접수를 막지 않는다")
    void submitFeedback_invalidThenValidRequest_doesNotConsumeLimit() throws Exception {
        // Given
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"정상 문의\"}"))
                .andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks WHERE user_id = ?",
                Long.class,
                userId
        )).isEqualTo(1L);
    }

    @Test
    @DisplayName("첫 접수 후 연속 접수는 거절하며 실패 요청은 저장하지 않는다")
    void submitFeedback_repeatedRequest_rejectsWithoutAdditionalRow() throws Exception {
        // Given
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"첫 문의\"}"))
                .andExpect(status().isCreated());

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"반복 문의\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks WHERE user_id = ?",
                Long.class,
                userId
        )).isEqualTo(1L);
    }
}
