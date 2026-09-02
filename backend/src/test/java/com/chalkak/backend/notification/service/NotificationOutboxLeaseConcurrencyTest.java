package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationDispatch;
import com.chalkak.backend.notification.domain.NotificationOutboxStatus;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationOutboxLeaseConcurrencyTest extends IntegrationTestSupport {

    private static final UUID FIRST_USER_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000001");
    private static final UUID SECOND_USER_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000002");
    private static final UUID TOPIC_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000003");
    private static final UUID FIRST_PHOTO_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000004");
    private static final UUID SECOND_PHOTO_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000005");
    private static final UUID FIRST_POST_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000006");
    private static final UUID SECOND_POST_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000007");

    @Autowired
    private NotificationOutboxClaimService claimService;

    @Autowired
    private NotificationOutboxStatusService statusService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.cleanNotificationTestData();
        insertPostFixtures();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.cleanNotificationTestData();
    }

    @Test
    @DisplayName("두 작업자가 동시에 선점해도 같은 Outbox를 중복 선점하지 않는다")
    void claimNext_twoConcurrentWorkers_claimDifferentOutboxes() throws Exception {
        // Given
        Instant dueAt = Instant.now().minusSeconds(1);
        UUID firstOutboxId = insertPendingOutbox(FIRST_POST_ID, dueAt);
        UUID secondOutboxId = insertPendingOutbox(SECOND_POST_ID, dueAt);
        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Callable<NotificationDispatch> claim = () -> {
            workersReady.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Outbox 선점 시작 신호를 기다리지 못했습니다.");
            }
            return claimService.claimNext().orElseThrow();
        };

        // When
        Future<NotificationDispatch> firstWorker = executor.submit(claim);
        Future<NotificationDispatch> secondWorker = executor.submit(claim);
        try {
            assertThat(workersReady.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            NotificationDispatch firstDispatch = firstWorker.get(10, TimeUnit.SECONDS);
            NotificationDispatch secondDispatch = secondWorker.get(10, TimeUnit.SECONDS);

            // Then
            assertThat(firstDispatch.outboxId()).isNotEqualTo(secondDispatch.outboxId());
            assertThat(new UUID[]{firstDispatch.outboxId(), secondDispatch.outboxId()})
                    .containsExactlyInAnyOrder(firstOutboxId, secondOutboxId);
            assertThat(firstDispatch.processingToken())
                    .isNotEqualTo(secondDispatch.processingToken());
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM notification_outboxes
                    WHERE status = 'PROCESSING'
                      AND attempt_count = 1
                    """, Integer.class)).isEqualTo(2);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("처리 lease가 만료된 Outbox는 새 토큰으로 재선점하고 시도 횟수를 올린다")
    void claimNext_expiredProcessing_replacesTokenAndIncrementsAttempt() {
        // Given
        Instant now = Instant.now();
        UUID oldProcessingToken = UUID.randomUUID();
        UUID outboxId = insertProcessingOutbox(
                FIRST_POST_ID,
                oldProcessingToken,
                now.minusSeconds(1),
                1
        );

        // When
        NotificationDispatch reclaimed = claimService.claimNext().orElseThrow();

        // Then
        assertThat(reclaimed.outboxId()).isEqualTo(outboxId);
        assertThat(reclaimed.attemptCount()).isEqualTo(2);
        assertThat(reclaimed.processingToken()).isNotEqualTo(oldProcessingToken);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT processing_token
                FROM notification_outboxes
                WHERE id = ?
                """, UUID.class, outboxId)).isEqualTo(reclaimed.processingToken());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT attempt_count
                FROM notification_outboxes
                WHERE id = ?
                """, Integer.class, outboxId)).isEqualTo(2);
    }

    @Test
    @DisplayName("재선점 전 토큰은 완료할 수 없고 현재 토큰만 완료할 수 있다")
    void markSent_reclaimedOutbox_onlyCurrentProcessingTokenSucceeds() {
        // Given
        Instant now = Instant.now();
        UUID oldProcessingToken = UUID.randomUUID();
        insertProcessingOutbox(
                FIRST_POST_ID,
                oldProcessingToken,
                now.minusSeconds(1),
                1
        );
        NotificationDispatch currentDispatch = claimService.claimNext().orElseThrow();
        NotificationDispatch oldTokenDispatch = new NotificationDispatch(
                currentDispatch.outboxId(),
                currentDispatch.postId(),
                currentDispatch.type(),
                currentDispatch.channel(),
                currentDispatch.target(),
                currentDispatch.attemptCount(),
                oldProcessingToken,
                currentDispatch.occurredAt()
        );

        // When
        boolean oldTokenCompleted = statusService.markSent(oldTokenDispatch);
        boolean currentTokenCompleted = statusService.markSent(currentDispatch);

        // Then
        assertThat(oldTokenCompleted).isFalse();
        assertThat(currentTokenCompleted).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status
                FROM notification_outboxes
                WHERE id = ?
                """, String.class, currentDispatch.outboxId()))
                .isEqualTo(NotificationOutboxStatus.SENT.name());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT processing_token IS NULL
                FROM notification_outboxes
                WHERE id = ?
                """, Boolean.class, currentDispatch.outboxId())).isTrue();
    }

    private void insertPostFixtures() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES
                    (?, 'notification-lease-first@example.com', 'ACTIVE',
                     'chalkak/signatures/test/original/notification-lease-first.png',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (?, 'notification-lease-second@example.com', 'ACTIVE',
                     'chalkak/signatures/test/original/notification-lease-second.png',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, FIRST_USER_ID, SECOND_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, 'Outbox lease 동시성', CURRENT_DATE + 100,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES
                    (?, 'chalkak/posts/test/original/notification-lease-first.webp',
                     '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (?, 'chalkak/posts/test/original/notification-lease-second.webp',
                     '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, FIRST_PHOTO_ID, SECOND_PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status,
                    created_at, updated_at
                ) VALUES
                    (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                FIRST_POST_ID, FIRST_USER_ID, TOPIC_ID, FIRST_PHOTO_ID,
                SECOND_POST_ID, SECOND_USER_ID, TOPIC_ID, SECOND_PHOTO_ID
        );
    }

    private UUID insertPendingOutbox(UUID postId, Instant dueAt) {
        return insertOutbox(postId, "PENDING", 0, dueAt, null, null);
    }

    private UUID insertProcessingOutbox(
            UUID postId,
            UUID processingToken,
            Instant leaseExpiresAt,
            int attemptCount
    ) {
        return insertOutbox(
                postId,
                "PROCESSING",
                attemptCount,
                null,
                processingToken,
                leaseExpiresAt
        );
    }

    private UUID insertOutbox(
            UUID postId,
            String status,
            int attemptCount,
            Instant nextAttemptAt,
            UUID processingToken,
            Instant leaseExpiresAt
    ) {
        UUID outboxId = UUID.randomUUID();
        Instant createdAt = Instant.now().minusSeconds(60);
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
                outboxId,
                postId,
                status,
                attemptCount,
                toOffsetDateTime(nextAttemptAt),
                processingToken,
                toOffsetDateTime(leaseExpiresAt),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
        return outboxId;
    }

    private Object toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atOffset(ZoneOffset.UTC);
    }
}
