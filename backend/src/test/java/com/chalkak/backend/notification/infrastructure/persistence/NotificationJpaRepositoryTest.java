package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.AiReviewOutcome;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.PostgresRepositoryTest;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@PostgresRepositoryTest
class NotificationJpaRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");

    @Autowired
    private NotificationJpaRepository repository;
    @Autowired
    private UserRepository users;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    private UUID userId;
    private UUID postId;
    private UUID topicId;
    private UUID photoId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        userId = users.save(UserFixture.create()).getId();
        entityManager.flush();
        postId = UUID.randomUUID();
        topicId = UUID.randomUUID();
        photoId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO topics(id, title, topic_date, starts_at, ends_at)
                        VALUES (?, '주제', CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')
                        """,
                topicId);
        jdbc.update("INSERT INTO photos(id,original_storage_key) VALUES (?, 'push/original')",
                photoId);
        jdbc.update(
                """
                        INSERT INTO posts(id, user_id, topic_id, photo_id, moderation_status)
                        VALUES (?, ?, ?, ?, 'APPROVED')
                        """,
                postId,
                userId,
                topicId,
                photoId
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPROVED", "REJECTED"})
    @DisplayName("삭제되지 않은 게시물의 본인 승인·반려 알림을 발송 검증용으로 조회한다")
    void findForPushByIdAndUserId_visiblePost_returnsNotification(String type) {
        // Given
        Notification notification = Notification.approved(
                userId,
                postId,
                UUID.randomUUID(),
                NOW,
                true
        );
        if (type.equals("REJECTED")) {
            jdbc.update("UPDATE posts SET moderation_status='REJECTED' WHERE id=?", postId);
            notification = Notification.rejected(
                    userId,
                    postId,
                    UUID.randomUUID(),
                    "반려 사유",
                    NOW,
                    true
            );
        }
        notificationId = repository.save(notification).getId();
        entityManager.flush();
        entityManager.clear();
        // When
        var result = repository.findForPushByIdAndUserId(
                notificationId,
                userId,
                ModerationStatus.APPROVED
        );
        // Then
        assertThat(result)
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(notificationId));
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, true", "VALIDATING, false", "PENDING, false", "REJECTED, false",
            "POST_DELETED, false", "TOPIC_DELETED, false", "PHOTO_DELETED, false",
            "AUTHOR_DELETED, false"
    })
    @DisplayName("좋아요 알림은 승인 상태이며 게시물·주제·사진·작성자가 삭제되지 않았을 때만 조회한다")
    void findForPushByIdAndUserId_likeMilestone_checksPublicVisibility(String state,
            boolean expected) {
        // Given
        notificationId = repository.save(Notification.likeMilestone(
                userId,
                postId,
                5L,
                NOW,
                true
        )).getId();
        entityManager.flush();
        updatePostState(state);
        entityManager.clear();

        // When
        var result = repository.findForPushByIdAndUserId(
                notificationId,
                userId,
                ModerationStatus.APPROVED
        );

        // Then
        assertThat(result.isPresent()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "POST_REJECTED, REJECTED", "AI_REVIEW_COMPLETED, PENDING",
            "AI_REVIEW_FAILED, REJECTED", "POST_APPROVED, TOPIC_DELETED",
            "AI_REVIEW_COMPLETED, PHOTO_DELETED"
    })
    @DisplayName("승인·반려·AI 리뷰 알림에는 좋아요 전용 공개 조건을 적용하지 않는다")
    void findForPushByIdAndUserId_otherPostNotification_preservesExistingConditions(
            String type,
            String state
    ) {
        // Given
        Notification notification = switch (type) {
            case "POST_REJECTED" -> Notification.rejected(
                    userId,
                    postId,
                    UUID.randomUUID(),
                    "반려 사유",
                    NOW,
                    true
            );
            case "AI_REVIEW_COMPLETED", "AI_REVIEW_FAILED" -> Notification.aiReview(
                    userId,
                    postId,
                    UUID.randomUUID(),
                    1,
                    type.equals("AI_REVIEW_COMPLETED")
                            ? AiReviewOutcome.COMPLETED
                            : AiReviewOutcome.FAILED_FINAL,
                    NOW,
                    true
            );
            default -> Notification.approved(
                    userId,
                    postId,
                    UUID.randomUUID(),
                    NOW,
                    true
            );
        };
        notificationId = repository.save(notification).getId();
        entityManager.flush();
        updatePostState(state);
        entityManager.clear();

        // When
        var result = repository.findForPushByIdAndUserId(
                notificationId,
                userId,
                ModerationStatus.APPROVED
        );

        // Then
        assertThat(result).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"postDeleted", "missingPost", "otherOwner", "missingNotification"})
    @DisplayName("삭제·누락 게시물과 다른 회원·없는 알림은 발송 조회에서 제외한다")
    void findForPushByIdAndUserId_invisibleNotification_returnsEmpty(String state) {
        // Given
        notificationId = repository
                .save(Notification.approved(
                        userId,
                        postId,
                        UUID.randomUUID(),
                        NOW,
                        true
                )).getId();
        entityManager.flush();
        if (state.equals("postDeleted")) {
            jdbc.update(
                    "UPDATE posts SET deleted_at=? WHERE id=?",
                    Timestamp.from(NOW),
                    postId
            );
        }
        if (state.equals("missingPost")) {
            jdbc.update("DELETE FROM posts WHERE id=?", postId);
        }
        UUID requestedUserId = userId;
        UUID requestedNotificationId = notificationId;
        if (state.equals("otherOwner")) {
            requestedUserId = UUID.randomUUID();
        }
        if (state.equals("missingNotification")) {
            requestedNotificationId = UUID.randomUUID();
        }
        entityManager.clear();
        // When
        var result = repository.findForPushByIdAndUserId(
                requestedNotificationId,
                requestedUserId,
                ModerationStatus.APPROVED
        );
        // Then
        assertThat(result).isEmpty();
    }

    private void updatePostState(String state) {
        switch (state) {
            case "POST_DELETED" -> jdbc.update(
                    "UPDATE posts SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    postId
            );
            case "TOPIC_DELETED" -> jdbc.update(
                    "UPDATE topics SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    topicId
            );
            case "PHOTO_DELETED" -> jdbc.update(
                    "UPDATE photos SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    photoId
            );
            case "AUTHOR_DELETED" -> jdbc.update(
                    "UPDATE users SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    userId
            );
            default -> jdbc.update(
                    "UPDATE posts SET moderation_status = CAST(? AS moderation_status) WHERE id = ?",
                    state,
                    postId
            );
        }
    }
}
