package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.repository.PostLikePushMilestoneRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostLikeMilestoneRelayServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432c101");
    private static final UUID POST_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432c104");

    @Autowired
    private PostLikePushMilestoneRepository milestoneRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private PushMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        insertPost();
        publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any()))
                .willReturn(PushPublicationResult.accepted("sqs-message-id"));
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("발행 대기 마일스톤은 ID를 사건 ID로 전달하고 SQS 수락 상태를 저장한다")
    void publishPendingMilestones_pendingMilestone_publishesCommandAndAcceptance() {
        // Given
        UUID milestoneId = saveMilestone(NOW);

        // When
        relay(NOW.plusSeconds(1)).publishPendingMilestones();

        // Then
        assertThat(getStatus(milestoneId)).isEqualTo("PUBLISHED");
        assertThat(getTime(milestoneId, "next_attempt_at")).isNull();
        assertThat(getTime(milestoneId, "sqs_published_at")).isEqualTo(NOW.plusSeconds(1));
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(publisher).publish(message.capture());
        assertThat(message.getValue()).isEqualTo(new PushMessage(
                milestoneId,
                null,
                USER_ID,
                NotificationType.POST_LIKE_MILESTONE,
                NotificationSourceType.POST,
                POST_ID,
                NOW,
                NOW.plusSeconds(1800)
        ));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일시 실패와 수락 불명은 1분 뒤 같은 사건으로 다시 발행한다")
    void publishPendingMilestones_retryableFailure_retriesAfterOneMinute(boolean unknown) {
        // Given
        UUID milestoneId = saveMilestone(NOW);
        PushPublicationResult result = PushPublicationResult.retry("RequestThrottled");
        if (unknown) {
            result = PushPublicationResult.unknown("InternalError");
        }
        given(publisher.publish(any())).willReturn(result);

        // When
        relay(NOW).publishPendingMilestones();

        // Then
        assertThat(getStatus(milestoneId)).isEqualTo("PENDING");
        assertThat(getTime(milestoneId, "next_attempt_at")).isEqualTo(NOW.plusSeconds(60));
        org.mockito.Mockito.clearInvocations(publisher);
        relay(NOW.plusSeconds(59)).publishPendingMilestones();
        verifyNoInteractions(publisher);
        given(publisher.publish(any()))
                .willReturn(PushPublicationResult.accepted("retry-accepted"));
        relay(NOW.plusSeconds(60)).publishPendingMilestones();
        assertThat(getStatus(milestoneId)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("영구 SQS 오류는 실패로 종료하고 다시 발행하지 않는다")
    void publishPendingMilestones_permanentFailure_stopsPublication() {
        // Given
        UUID milestoneId = saveMilestone(NOW);
        given(publisher.publish(any())).willReturn(PushPublicationResult.failed("AccessDenied"));

        // When
        relay(NOW).publishPendingMilestones();

        // Then
        assertThat(getStatus(milestoneId)).isEqualTo("FAILED");
        assertThat(getTime(milestoneId, "next_attempt_at")).isNull();
        org.mockito.Mockito.clearInvocations(publisher);
        relay(NOW.plusSeconds(1)).publishPendingMilestones();
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("마일스톤 생성 후 30분 직전까지만 SQS에 발행한다")
    void publishPendingMilestones_deadlineBoundary_enforcesDeadline(long seconds) {
        // Given
        UUID milestoneId = saveMilestone(NOW);

        // When
        relay(NOW.plusSeconds(seconds)).publishPendingMilestones();

        // Then
        if (seconds < 1800) {
            assertThat(getStatus(milestoneId)).isEqualTo("PUBLISHED");
            return;
        }
        assertThat(getStatus(milestoneId)).isEqualTo("EXPIRED");
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("같은 마일스톤을 동시에 조회해도 잠금을 획득한 Relay만 발행한다")
    void publishPendingMilestones_concurrentRelays_publishesOnlyOnce() throws Exception {
        // Given
        UUID milestoneId = saveMilestone(NOW);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(publisher.publish(any())).willAnswer(call -> {
            publishing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test publication latch timed out");
            }
            return PushPublicationResult.accepted("only-once");
        });

        // When
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> relay(NOW).publishPendingMilestones());
            try {
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> relay(NOW).publishPendingMilestones())
                        .get(3, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }

        // Then
        assertThat(getStatus(milestoneId)).isEqualTo("PUBLISHED");
        verify(publisher).publish(any());
    }

    @Test
    @DisplayName("한 마일스톤의 발행 예외는 다음 마일스톤의 발행을 막지 않는다")
    void publishPendingMilestones_onePublicationFails_continuesNextMilestone() {
        // Given
        UUID failedId = saveMilestone(NOW, 5L);
        UUID succeededId = saveMilestone(NOW.plusSeconds(1), 10L);
        given(publisher.publish(any()))
                .willThrow(new IllegalStateException("SQS unavailable"))
                .willReturn(PushPublicationResult.accepted("accepted-next"));

        // When
        relay(NOW.plusSeconds(1)).publishPendingMilestones();

        // Then
        assertThat(getStatus(failedId)).isEqualTo("PENDING");
        assertThat(getStatus(succeededId)).isEqualTo("PUBLISHED");
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(2)).publish(any());
    }

    private PostLikeMilestoneRelayService relay(Instant now) {
        return new PostLikeMilestoneRelayService(
                milestoneRepository,
                publisher,
                Clock.fixed(now, ZoneOffset.UTC),
                transactionManager
        );
    }

    private UUID saveMilestone(Instant createdAt) {
        return saveMilestone(createdAt, 5L);
    }

    private UUID saveMilestone(Instant createdAt, long count) {
        return new TransactionTemplate(transactionManager).execute(status -> milestoneRepository
                .save(PostLikePushMilestone.create(
                        POST_ID,
                        USER_ID,
                        count,
                        createdAt
                ))
                .getId());
    }

    private String getStatus(UUID milestoneId) {
        return jdbcTemplate.queryForObject(
                "SELECT sqs_publish_status FROM post_like_push_milestones WHERE id = ?",
                String.class,
                milestoneId
        );
    }

    private Instant getTime(UUID milestoneId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM post_like_push_milestones WHERE id = ?",
                (row, rowNumber) -> row.getTimestamp(1) == null
                        ? null
                        : row.getTimestamp(1).toInstant(),
                milestoneId
        );
    }

    private void insertPost() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (?, 'milestone-relay@example.com', 'ACTIVE', 'signatures/relay',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES ('019a47c0-3db8-72dc-8f2d-07a15432c102', 'Relay 주제', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, created_at, updated_at)
                VALUES ('019a47c0-3db8-72dc-8f2d-07a15432c103', 'posts/relay',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                        ) VALUES (?, ?, '019a47c0-3db8-72dc-8f2d-07a15432c102',
                            '019a47c0-3db8-72dc-8f2d-07a15432c103', 'APPROVED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                POST_ID,
                USER_ID
        );
    }
}
