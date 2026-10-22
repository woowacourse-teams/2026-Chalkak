package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.AiReviewOutcome;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @ParameterizedTest
    @CsvSource({
            "VALIDATING, false, false", "PENDING, false, false", "APPROVED, false, false",
            "REJECTED, false, true", "VALIDATING, true, true", "PENDING, true, true",
            "APPROVED, true, true", "REJECTED, true, true"
    })
    @DisplayName("오늘 주제에 처리 중·승인 대기·승인 게시물이 있으면 제외하고 반려·삭제만 있으면 포함한다")
    void findTopicPushRecipientIds_postState_selectsEligibleUsers(
            ModerationStatus status,
            boolean deleted,
            boolean eligible
    ) {
        // Given
        UUID authorId = insertUser();
        UUID nonAuthorId = insertUser();
        UUID topicId = insertTopic("2026-10-12");
        insertPost(
                authorId,
                topicId,
                status,
                deleted
        );
        entityManager.flush();
        entityManager.clear();

        // When
        List<UUID> recipients = notificationRepository.findTopicPushRecipientIds(topicId);

        // Then
        assertThat(recipients).contains(nonAuthorId);
        if (eligible) {
            assertThat(recipients).containsExactlyInAnyOrder(authorId, nonAuthorId);
            return;
        }
        assertThat(recipients).containsExactly(nonAuthorId);
    }

    @Test
    @DisplayName("다른 주제에 승인된 게시물이 있어도 해당 주제 발송 대상에 포함한다")
    void findTopicPushRecipientIds_otherTopicPost_includesUser() {
        // Given
        UUID authorId = insertUser();
        UUID todayTopicId = insertTopic("2026-10-12");
        UUID otherTopicId = insertTopic("2026-10-11");
        insertPost(
                authorId,
                otherTopicId,
                ModerationStatus.APPROVED,
                false
        );
        entityManager.flush();
        entityManager.clear();

        // When
        List<UUID> recipients = notificationRepository.findTopicPushRecipientIds(todayTopicId);

        // Then
        assertThat(recipients).contains(authorId);
    }

    @Test
    @DisplayName("반려 후 다시 제출한 승인 대기 게시물이 있으면 주제 발송 대상에서 제외한다")
    void findTopicPushRecipientIds_resubmittedPost_excludesUser() {
        // Given
        UUID authorId = insertUser();
        UUID topicId = insertTopic("2026-10-12");
        insertPost(
                authorId,
                topicId,
                ModerationStatus.REJECTED,
                false
        );
        insertPost(
                authorId,
                topicId,
                ModerationStatus.PENDING,
                false
        );
        entityManager.flush();
        entityManager.clear();

        // When
        List<UUID> recipients = notificationRepository.findTopicPushRecipientIds(topicId);

        // Then
        assertThat(recipients).doesNotContain(authorId);
    }

    @Test
    @DisplayName("AI 리뷰 알림 저장은 처음이면 1, 같은 회원·같은 키면 0, 다른 회차면 1을 돌려준다")
    void insertIfAbsent_byEventKey_returnsInsertedRowCount() {
        // Given
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        UUID aiReviewId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-21T01:00:00Z");

        // When
        int first = notificationRepository.insertIfAbsent(Notification.aiReview(
                userId,
                postId,
                aiReviewId,
                1,
                AiReviewOutcome.COMPLETED,
                createdAt,
                true
        ));
        int duplicate = notificationRepository.insertIfAbsent(Notification.aiReview(
                userId,
                postId,
                aiReviewId,
                1,
                AiReviewOutcome.FAILED_RETRYABLE,
                createdAt.plusSeconds(1),
                true
        ));
        int nextRound = notificationRepository.insertIfAbsent(Notification.aiReview(
                userId,
                postId,
                aiReviewId,
                2,
                AiReviewOutcome.FAILED_RETRYABLE,
                createdAt.plusSeconds(2),
                true
        ));

        // Then
        assertThat(first).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(nextRound).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ?",
                Integer.class,
                userId
        )).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"true, PENDING, true", "false, NOT_REQUIRED, false"})
    @DisplayName("AI 리뷰 알림 저장은 도메인이 만든 종류·대상·문구·발행 상태를 그대로 저장한다")
    void insertIfAbsent_notification_persistsDomainValues(
            boolean pushEnabled,
            String publishStatus,
            boolean hasNextAttemptAt
    ) {
        // Given
        UUID userId = insertUser();
        UUID postId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-10-21T01:00:00Z");
        Notification notification = Notification.aiReview(
                userId,
                postId,
                UUID.randomUUID(),
                3,
                AiReviewOutcome.FAILED_FINAL,
                createdAt,
                pushEnabled
        );

        // When
        notificationRepository.insertIfAbsent(notification);

        // Then
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT * FROM notifications WHERE user_id = ?",
                userId
        );
        assertThat(row).containsEntry("type", "AI_REVIEW_FAILED")
                .containsEntry("source_type", "POST")
                .containsEntry("source_id", postId)
                .containsEntry("event_key", notification.getEventKey())
                .containsEntry("title", "AI 피드백을 만들지 못했어요")
                .containsEntry("body", "이 사진은 더 이상 AI 피드백을 요청할 수 없어요.")
                .containsEntry("sqs_publish_status", publishStatus);
        assertThat(((Timestamp) row.get("created_at")).toInstant()).isEqualTo(createdAt);
        assertThat(row.get("next_attempt_at") != null).isEqualTo(hasNextAttemptAt);
        assertThat(row.get("read_at")).isNull();
        assertThat(row.get("payload")).isNull();
    }

    private UUID insertUser() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO users(id,email,status,signature_original_storage_key)
                        VALUES (?,?,'ACTIVE',?)
                        """,
                id,
                id + "@participation.test",
                "signatures/" + id
        );
        return id;
    }

    private UUID insertTopic(String date) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO topics(id,title,topic_date,starts_at,ends_at)
                        VALUES (?,'빛과 그림자',?,?::date,?::date + INTERVAL '1 day')
                        """,
                id,
                Date.valueOf(date),
                Date.valueOf(date),
                Date.valueOf(date)
        );
        return id;
    }

    private void insertPost(
            UUID userId,
            UUID topicId,
            ModerationStatus status,
            boolean deleted
    ) {
        UUID photoId = UUID.randomUUID();
        jdbc.update(
                """
                        INSERT INTO photos(id,original_storage_key,thumbnail_storage_key)
                        VALUES (?,?,?)
                        """,
                photoId,
                "original/" + photoId,
                "thumbnail/" + photoId
        );
        Timestamp deletedAt = null;
        if (deleted) {
            deletedAt = Timestamp.from(Instant.parse("2026-10-12T00:00:00Z"));
        }
        jdbc.update(
                """
                        INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status,deleted_at,created_at)
                        VALUES (?,?,?,?,CAST(? AS moderation_status),?,'2026-10-10T00:00:00Z')
                        """,
                UUID.randomUUID(),
                userId,
                topicId,
                photoId,
                status.name(),
                deletedAt
        );
    }

}
