package com.chalkak.backend.feedback.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.support.PostgresRepositoryTest;
import jakarta.persistence.EntityManager;
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
}
