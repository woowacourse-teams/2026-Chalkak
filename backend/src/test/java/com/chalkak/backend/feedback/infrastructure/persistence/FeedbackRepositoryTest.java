package com.chalkak.backend.feedback.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.support.PostgresRepositoryTest;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresRepositoryTest
class FeedbackRepositoryTest {

    private static final UUID USER_ID = UUID.fromString("0198fc30-0000-7000-8000-000000000001");

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, app_version,
                    created_at, updated_at
                ) VALUES (
                    ?, 'author@example.com', 'ACTIVE',
                    'signatures/author-original', 'signatures/author-thumbnail',
                    '1.2.3', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                USER_ID);
    }

    @Test
    @DisplayName("한 회원이 피드백을 여러 번 남길 수 있다")
    void save_repeatedFeedback_storesEverySubmission() {
        // When
        feedbackRepository.save(Feedback.create(USER_ID, "첫 번째 의견"));
        feedbackRepository.save(Feedback.create(USER_ID, "두 번째 의견"));
        entityManager.flush();

        // Then
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM feedbacks WHERE user_id = ?",
                Long.class,
                USER_ID
        );
        assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("접수 이력은 해당 회원의 최근 24시간만 최신순으로 조회하며 정확한 경계는 제외한다")
    void findSubmissionTimesAfter_userAndTimeBoundary_returnsRecentTimesInOrder() {
        // Given
        Instant now = Instant.parse("2026-11-03T06:00:00Z");
        Instant boundary = now.minusSeconds(86400);
        insertFeedback(USER_ID, boundary);
        insertFeedback(USER_ID, boundary.minusSeconds(1));
        insertFeedback(USER_ID, boundary.plusSeconds(1));
        insertFeedback(USER_ID, now.minusSeconds(60));
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, 'other@chalkak.test', 'signatures/other', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                otherUserId);
        insertFeedback(otherUserId, now);

        // When & Then
        assertThat(feedbackRepository.findSubmissionTimesAfter(USER_ID, boundary))
                .containsExactly(now.minusSeconds(60), boundary.plusSeconds(1));
    }

    private void insertFeedback(UUID userId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO feedbacks (user_id, content, created_at) VALUES (?, '문의', ?)",
                userId,
                Timestamp.from(createdAt)
        );
    }
}
