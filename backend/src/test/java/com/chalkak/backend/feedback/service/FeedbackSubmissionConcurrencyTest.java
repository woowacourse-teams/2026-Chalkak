package com.chalkak.backend.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.UserStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FeedbackSubmissionConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private FeedbackService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userId;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, 'author@chalkak.test', 'signatures/author', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId);
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @ParameterizedTest
    @EnumSource(UserStatus.class)
    @DisplayName("정상·정지 회원 모두 같은 계정의 첫 동시 문의는 한 건만 저장한다")
    void submit_concurrentFirstSubmissions_storesOnlyOne(UserStatus status) throws Exception {
        // Given
        jdbcTemplate.update(
                "UPDATE users SET status = CAST(? AS user_status) WHERE id = ?",
                status.name(),
                userId
        );

        // When
        List<Boolean> results = submitConcurrently();

        // Then
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(countFeedbacks()).isEqualTo(1);
    }

    @Test
    @DisplayName("최근 네 건이 있을 때 동시 문의를 보내도 다섯 건을 넘기지 않는다")
    void submit_concurrentFifthSubmissions_keepsDailyLimit() throws Exception {
        // Given
        Instant now = Instant.now();
        for (int index = 1; index <= 4; index++) {
            jdbcTemplate.update(
                    "INSERT INTO feedbacks (user_id, content, created_at) VALUES (?, '기존 문의', ?)",
                    userId,
                    Timestamp.from(now.minusSeconds(index * 120L))
            );
        }

        // When
        List<Boolean> results = submitConcurrently();

        // Then
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(countFeedbacks()).isEqualTo(5);
    }

    private List<Boolean> submitConcurrently() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> requests = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            for (int index = 0; index < 2; index++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 접수 시작 신호를 받지 못했습니다.");
                    }
                    try {
                        service.submit(userId, "동시 문의");
                        return true;
                    } catch (BusinessException exception) {
                        assertThat(exception.getMessage()).contains("다음 접수 가능 시각(UTC)");
                        return false;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> request : requests) {
                results.add(request.get(20, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private long countFeedbacks() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks WHERE user_id = ?",
                Long.class,
                userId
        );
    }
}
