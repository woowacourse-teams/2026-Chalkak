package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
    void findTopicPushEnabledNonParticipantIds_postState_selectsEligibleUsers(
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
        List<UUID> recipients = notificationRepository
                .findTopicPushEnabledNonParticipantIds(topicId);

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
    void findTopicPushEnabledNonParticipantIds_otherTopicPost_includesUser() {
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
        List<UUID> recipients = notificationRepository
                .findTopicPushEnabledNonParticipantIds(todayTopicId);

        // Then
        assertThat(recipients).contains(authorId);
    }

    @Test
    @DisplayName("반려 후 다시 제출한 승인 대기 게시물이 있으면 주제 발송 대상에서 제외한다")
    void findTopicPushEnabledNonParticipantIds_resubmittedPost_excludesUser() {
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
        List<UUID> recipients = notificationRepository
                .findTopicPushEnabledNonParticipantIds(topicId);

        // Then
        assertThat(recipients).doesNotContain(authorId);
    }

    @ParameterizedTest
    @CsvSource({
            "VALIDATING, false, true", "PENDING, false, true", "APPROVED, false, true",
            "REJECTED, false, false", "VALIDATING, true, false", "PENDING, true, false",
            "APPROVED, true, false", "REJECTED, true, false"
    })
    @DisplayName("오늘 주제에 처리 중·승인 대기·승인 게시물이 있으면 참여자로 포함하고 반려·삭제만 있으면 제외한다")
    void findTopicPushEnabledParticipantIds_postState_selectsParticipants(
            ModerationStatus status,
            boolean deleted,
            boolean participant
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
        List<UUID> participants = notificationRepository
                .findTopicPushEnabledParticipantIds(topicId);

        // Then
        assertThat(participants).doesNotContain(nonAuthorId);
        if (participant) {
            assertThat(participants).containsExactly(authorId);
            return;
        }
        assertThat(participants).isEmpty();
    }

    @Test
    @DisplayName("다른 주제에만 게시물이 있으면 오늘 주제 참여자에서 제외한다")
    void findTopicPushEnabledParticipantIds_otherTopicPost_excludesUser() {
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

        // When & Then
        assertThat(notificationRepository.findTopicPushEnabledParticipantIds(todayTopicId))
                .doesNotContain(authorId);
    }

    @Test
    @DisplayName("반려 후 다시 제출한 승인 대기 게시물이 있으면 참여자로 포함한다")
    void findTopicPushEnabledParticipantIds_resubmittedPost_includesUser() {
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

        // When & Then
        assertThat(notificationRepository.findTopicPushEnabledParticipantIds(topicId))
                .containsExactly(authorId);
    }

    @Test
    @DisplayName("주제 알림을 끈 참여자와 정지된 참여자는 제외한다")
    void findTopicPushEnabledParticipantIds_disabledOrBannedUser_excludesUser() {
        // Given
        UUID disabledId = insertUser();
        UUID bannedId = insertUser();
        UUID topicId = insertTopic("2026-10-12");
        insertPost(
                disabledId,
                topicId,
                ModerationStatus.APPROVED,
                false
        );
        insertPost(
                bannedId,
                topicId,
                ModerationStatus.APPROVED,
                false
        );
        jdbc.update("UPDATE users SET topic_push_enabled = false WHERE id = ?", disabledId);
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", bannedId);
        entityManager.flush();
        entityManager.clear();

        // When & Then
        assertThat(notificationRepository.findTopicPushEnabledParticipantIds(topicId)).isEmpty();
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
