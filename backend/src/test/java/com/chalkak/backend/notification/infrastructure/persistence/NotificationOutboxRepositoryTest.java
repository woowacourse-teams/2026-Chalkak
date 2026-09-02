package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationOutbox;
import com.chalkak.backend.notification.domain.NotificationOutboxStatus;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationOutboxRepositoryTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0199a001-0000-7000-8000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("0199a001-0000-7000-8000-000000000002");
    private static final UUID PHOTO_ID =
            UUID.fromString("0199a001-0000-7000-8000-000000000003");
    private static final UUID POST_ID =
            UUID.fromString("0199a001-0000-7000-8000-000000000004");
    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'notification-outbox@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/notification-outbox.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '알림 테스트 주제', CURRENT_DATE + 100,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/test/original/notification-outbox.webp',
                    '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID);
    }

    @Test
    @DisplayName("같은 게시물의 같은 채널 알림은 여러 번 적재해도 한 건만 남긴다")
    void createPostModerationPending_duplicateRequest_createsOnce() {
        // When
        boolean firstCreated = notificationOutboxRepository.createPostModerationPending(
                POST_ID,
                NOW
        );
        boolean secondCreated = notificationOutboxRepository.createPostModerationPending(
                POST_ID,
                NOW.plusSeconds(1)
        );

        // Then
        assertThat(firstCreated).isTrue();
        assertThat(secondCreated).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outboxes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isEqualTo(1);
    }

    @Test
    @DisplayName("도래한 대기 알림과 임대가 만료된 처리 알림만 잠금 조회한다")
    void findClaimableForUpdate_mixedRows_returnsDueAndExpiredLease() {
        // Given
        UUID dueId = insertOutbox("PENDING", NOW.minusSeconds(1), null, null, 0);
        insertOutbox("PENDING", NOW.plusSeconds(1), null, null, 0);
        UUID expiredLeaseId = insertOutbox(
                "PROCESSING",
                null,
                UUID.randomUUID(),
                NOW.minusSeconds(1),
                1
        );
        insertOutbox(
                "PROCESSING",
                null,
                UUID.randomUUID(),
                NOW.plusSeconds(1),
                1
        );

        // When
        List<NotificationOutbox> claimable =
                notificationOutboxRepository.findClaimableForUpdate(NOW, 10);

        // Then
        assertThat(claimable).extracting(NotificationOutbox::getId)
                .containsExactlyInAnyOrder(dueId, expiredLeaseId);
    }

    @Test
    @DisplayName("선점 토큰과 시도 횟수가 일치하는 작업자만 발송 완료를 기록한다")
    void markSent_staleAndCurrentToken_onlyCurrentWorkerUpdates() {
        // Given
        UUID processingToken = UUID.randomUUID();
        UUID outboxId = insertOutbox(
                "PROCESSING",
                null,
                processingToken,
                NOW.plusSeconds(30),
                2
        );

        // When
        boolean staleUpdated = notificationOutboxRepository.markSent(
                outboxId,
                UUID.randomUUID(),
                1,
                NOW
        );
        boolean currentUpdated = notificationOutboxRepository.markSent(
                outboxId,
                processingToken,
                2,
                NOW
        );
        entityManager.clear();

        // Then
        assertThat(staleUpdated).isFalse();
        assertThat(currentUpdated).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM notification_outboxes WHERE id = ?",
                String.class,
                outboxId
        )).isEqualTo(NotificationOutboxStatus.SENT.name());
    }

    @Test
    @DisplayName("게시물을 물리 삭제하면 더 이상 의미가 없는 Outbox도 함께 삭제한다")
    void deletePost_existingOutbox_cascadesOutboxDeletion() {
        // Given
        notificationOutboxRepository.createPostModerationPending(POST_ID, NOW);

        // When
        int deletedPosts = jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);

        // Then
        assertThat(deletedPosts).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_outboxes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isZero();
    }

    private UUID insertOutbox(
            String status,
            Instant nextAttemptAt,
            UUID processingToken,
            Instant leaseExpiresAt,
            int attemptCount
    ) {
        UUID id = UUID.randomUUID();
        UUID notificationPostId = UUID.randomUUID();
        UUID notificationPhotoId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (
                    ?, ?, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                notificationPhotoId,
                "chalkak/posts/test/original/" + notificationPhotoId + ".webp"
        );
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, 'PENDING',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, notificationPostId, USER_ID, TOPIC_ID, notificationPhotoId);
        jdbcTemplate.update("""
                INSERT INTO notification_outboxes (
                    id, post_id, type, channel, target, status, attempt_count,
                    next_attempt_at, processing_token, lease_expires_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, 'POST_MODERATION_PENDING', 'SLACK',
                    'ADMIN_MODERATION_REVIEWERS', CAST(? AS notification_outbox_status), ?,
                    ?, ?, ?, ?, ?
                )
                """,
                id,
                notificationPostId,
                status,
                attemptCount,
                nextAttemptAt == null ? null : nextAttemptAt.atOffset(ZoneOffset.UTC),
                processingToken,
                leaseExpiresAt == null ? null : leaseExpiresAt.atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC),
                NOW.minusSeconds(60).atOffset(ZoneOffset.UTC)
        );
        return id;
    }
}
