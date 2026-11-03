package com.chalkak.backend.feedback.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chalkak.backend.auth.infrastructure.infra.token.access.JwtAccessTokenProvider;
import com.chalkak.backend.feedback.service.FeedbackService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class FeedbackDeletionIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FeedbackService service;

    @Autowired
    private JwtAccessTokenProvider accessTokenProvider;

    @MockitoBean
    private Clock clock;

    private Instant now;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        given(clock.instant()).willReturn(now);
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @ParameterizedTest
    @EnumSource(value = UserStatus.class, names = {"ACTIVE", "BANNED"})
    @DisplayName("정상·정지 회원이 본인 문의를 삭제하면 원문은 보존하고 목록·상세에서 숨긴다")
    void deleteFeedback_activeOrBannedUser_commitsSoftDeletion(UserStatus userStatus)
            throws Exception {
        // Given: 테스트 전체 트랜잭션 없이 실제 요청의 커밋 결과를 확인한다.
        UUID userId = createUser(userStatus);
        UUID feedbackId = createFeedback(userId);
        String token = tokenFor(userId);
        Map<String, Object> original = storedContent(feedbackId);

        // When
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());

        // Then
        assertThat(storedContent(feedbackId)).isEqualTo(original);
        assertThat(deletedAt(feedbackId)).isEqualTo(now);
        mockMvc.perform(get("/api/v1/feedbacks").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feedbacks").isEmpty());
        mockMvc.perform(get("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("타인 문의 삭제는 404로 거절하고 삭제 상태를 바꾸지 않는다")
    void deleteFeedback_otherUserFeedback_returnsNotFoundWithoutMutation() throws Exception {
        // Given
        UUID authorId = createUser(UserStatus.ACTIVE);
        UUID viewerId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(authorId);

        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, tokenFor(viewerId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("문의를 찾을 수 없습니다."));
        assertThat(deletedAt(feedbackId)).isNull();
    }

    @Test
    @DisplayName("없는 문의 삭제도 타인 문의와 같은 404 응답을 반환한다")
    void deleteFeedback_missingFeedback_returnsNotFound() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);

        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, tokenFor(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("문의를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("탈퇴한 회원은 남은 토큰으로 본인 문의를 삭제할 수 없다")
    void deleteFeedback_withdrawnUser_returnsUnauthorizedWithoutMutation() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        String token = tokenFor(userId);
        jdbcTemplate.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?", userId);

        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
        assertThat(deletedAt(feedbackId)).isNull();
    }

    @Test
    @DisplayName("비로그인 요청은 문의를 삭제할 수 없다")
    void deleteFeedback_unauthenticated_returnsUnauthorized() throws Exception {
        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("같은 문의를 다시 삭제해도 성공하고 최초 삭제 시각을 유지한다")
    void deleteFeedback_repeatedRequest_keepsOriginalTime() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        String token = tokenFor(userId);
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        given(clock.instant()).willReturn(now.plusSeconds(60));

        // When & Then
        mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(deletedAt(feedbackId)).isEqualTo(now);
    }

    @Test
    @DisplayName("최근 문의 다섯 건을 모두 삭제해도 새 문의 접수 제한은 유지한다")
    void deleteFeedback_dailyLimitReached_doesNotResetSubmissionCount() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        String token = tokenFor(userId);
        for (int index = 0; index < 5; index++) {
            UUID feedbackId = createFeedback(userId);
            mockMvc.perform(delete("/api/v1/feedbacks/{feedbackId}", feedbackId)
                    .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNoContent());
        }

        // When & Then
        mockMvc.perform(post("/api/v1/feedbacks")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"삭제 후 새 문의\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BUSINESS_ERROR"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM feedbacks", Long.class))
                .isEqualTo(5L);
    }

    @Test
    @DisplayName("동시 삭제도 모두 성공하고 첫 번째 삭제 시각을 보존한다")
    void deleteFeedback_concurrentRequests_preservesFirstDeletionTime() throws Exception {
        // Given
        UUID userId = createUser(UserStatus.ACTIVE);
        UUID feedbackId = createFeedback(userId);
        AtomicInteger sequence = new AtomicInteger();
        given(clock.instant())
                .willAnswer(invocation -> now.plusSeconds(sequence.getAndIncrement()));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> deleteRequest = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("동시 삭제 시작 신호를 받지 못했습니다.");
                }
                service.deleteFeedback(userId, feedbackId);
                return null;
            };
            List<Future<Void>> requests = List.of(executor.submit(deleteRequest),
                    executor.submit(deleteRequest));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Void> request : requests) {
                request.get(20, TimeUnit.SECONDS);
            }
        }

        // Then
        assertThat(deletedAt(feedbackId)).isEqualTo(now);
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

    private Map<String, Object> storedContent(UUID feedbackId) {
        return jdbcTemplate.queryForMap(
                "SELECT id, user_id, content, created_at FROM feedbacks WHERE id = ?", feedbackId);
    }

    private Instant deletedAt(UUID feedbackId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT deleted_at FROM feedbacks WHERE id = ?",
                Timestamp.class,
                feedbackId
        );
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String tokenFor(UUID userId) {
        return "Bearer " + accessTokenProvider.issueForSession(userId, UUID.randomUUID()).value();
    }
}
