package com.chalkak.backend.feedback.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import com.chalkak.backend.feedback.repository.FeedbackSlice;
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

    private static final Instant CREATED_AT = Instant.parse("2026-11-03T06:00:00Z");

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
        feedbackRepository.save(Feedback.create(
                USER_ID,
                "첫 번째 의견",
                CREATED_AT
        ));
        feedbackRepository.save(Feedback.create(
                USER_ID,
                "두 번째 의견",
                CREATED_AT
        ));
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
    @DisplayName("삭제용 잠금 조회는 본인 삭제 이력을 포함하고 타인은 제외한다")
    void findByIdAndUserIdForUpdate_deletedOwnFeedback_preservesOwnership() {
        // Given
        Feedback feedback = feedbackRepository.save(Feedback.create(
                USER_ID,
                "삭제된 문의",
                CREATED_AT
        ));
        UUID feedbackId = feedback.getId();
        feedback.delete(Instant.parse("2026-11-03T07:00:00Z"));
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(feedbackRepository.findByIdAndUserIdForUpdate(feedbackId, USER_ID))
                .get().extracting(Feedback::getContent).isEqualTo("삭제된 문의");
        assertThat(feedbackRepository.findByIdAndUserIdForUpdate(feedbackId, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    @DisplayName("상세 조회는 식별자가 맞아도 작성자가 다르면 반환하지 않는다")
    void findVisibleByIdAndUserId_differentUser_returnsEmpty() {
        // Given
        Feedback feedback = feedbackRepository.save(Feedback.create(
                USER_ID,
                "비공개 문의",
                CREATED_AT
        ));
        UUID feedbackId = feedback.getId();
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(feedbackRepository.findVisibleByIdAndUserId(feedbackId, USER_ID)).isPresent();
        assertThat(feedbackRepository.findVisibleByIdAndUserId(feedbackId, UUID.randomUUID()))
                .isEmpty();
    }

    @Test
    @DisplayName("삭제 표시가 있는 문의는 작성자 본인의 상세 조회에서도 제외한다")
    void findVisibleByIdAndUserId_deletedFeedback_returnsEmpty() {
        // Given
        Feedback feedback = feedbackRepository.save(Feedback.create(
                USER_ID,
                "삭제된 문의",
                CREATED_AT
        ));
        UUID feedbackId = feedback.getId();
        entityManager.flush();
        jdbcTemplate.update("UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                feedbackId);
        entityManager.clear();

        // When & Then
        assertThat(feedbackRepository.findVisibleByIdAndUserId(feedbackId, USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("본인 문의만 최신순으로 페이지를 나누고 같은 시각은 식별자 내림차순으로 정렬한다")
    void findVisibleByUserId_ownFeedbackWithTiedTimes_returnsStablePages() {
        // Given
        Instant createdAt = Instant.parse("2026-11-03T06:00:00Z");
        UUID olderId = UUID.fromString("0198fc30-0000-7000-8000-000000000011");
        UUID newerId = UUID.fromString("0198fc30-0000-7000-8000-000000000012");
        insertFeedback(USER_ID, createdAt.minusSeconds(1));
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at) VALUES (?, ?, '동일 시각 문의', ?), (?, ?, '동일 시각 문의', ?)",
                olderId,
                USER_ID,
                Timestamp.from(createdAt),
                newerId,
                USER_ID,
                Timestamp.from(createdAt)
        );
        UUID otherUserId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, 'other@chalkak.test', 'signatures/other')",
                otherUserId
        );
        insertFeedback(otherUserId, createdAt.plusSeconds(1));
        UUID deletedId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO feedbacks (id, user_id, content, created_at, deleted_at) VALUES (?, ?, '삭제된 문의', ?, CURRENT_TIMESTAMP)",
                deletedId,
                USER_ID,
                Timestamp.from(createdAt.plusSeconds(2))
        );

        // When
        FeedbackSlice first = feedbackRepository.findVisibleByUserId(
                USER_ID,
                1,
                2
        );
        FeedbackSlice second = feedbackRepository.findVisibleByUserId(
                USER_ID,
                2,
                2
        );
        FeedbackSlice beyondLast = feedbackRepository.findVisibleByUserId(
                USER_ID,
                3,
                2
        );

        // Then
        assertThat(first.feedbacks()).extracting(Feedback::getId).containsExactly(newerId, olderId);
        assertThat(first.hasNext()).isTrue();
        assertThat(second.feedbacks()).hasSize(1)
                .allMatch(feedback -> feedback.getUserId().equals(USER_ID));
        assertThat(second.feedbacks().getFirst().getCreatedAt())
                .isEqualTo(createdAt.minusSeconds(1));
        assertThat(second.hasNext()).isFalse();
        assertThat(beyondLast.feedbacks()).isEmpty();
        assertThat(beyondLast.hasNext()).isFalse();
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
        jdbcTemplate.update(
                "UPDATE feedbacks SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                USER_ID
        );
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
