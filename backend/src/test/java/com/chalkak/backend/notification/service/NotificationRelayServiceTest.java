package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
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

class NotificationRelayServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-06T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    private PushMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, 'relay@example.com', CAST('ACTIVE' AS user_status), 'chalkak/signatures/test/original.webp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                USER_ID);
        publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any()))
                .willReturn(PushPublicationResult.accepted("sqs-message-id"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS relay_reject_update ON notifications");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS relay_reject_update()");
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("커밋된 발행 대기는 식별 정보만 전달하고 SQS 수락 상태를 저장한다")
    void publishPendingNotifications_committedPending_publishesAndPersistsAcceptance() {
        // Given
        UUID id = saveNotification(NOW, true);

        // When
        relay(NOW.plusSeconds(1)).publishPendingNotifications();

        // Then
        assertThat(getStatus(id)).isEqualTo("PUBLISHED");
        assertThat(getTime(id, "next_attempt_at")).isNull();
        assertThat(getTime(id, "sqs_published_at")).isEqualTo(NOW.plusSeconds(1));
        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        org.mockito.Mockito.verify(publisher).publish(message.capture());
        assertThat(message.getValue().notificationId()).isEqualTo(id);
        assertThat(message.getValue().userId()).isEqualTo(USER_ID);
        assertThat(message.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(message.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("일시 실패는 1분 뒤 다시 시도하고 그 전에는 발행하지 않는다")
    void publishPendingNotifications_transientFailure_retriesAtOneMinute() {
        // Given
        UUID id = saveNotification(NOW, true);
        given(publisher.publish(any()))
                .willReturn(PushPublicationResult.retry("ServiceUnavailable"));

        // When
        relay(NOW).publishPendingNotifications();

        // Then
        assertThat(getStatus(id)).isEqualTo("PENDING");
        assertThat(getTime(id, "next_attempt_at")).isEqualTo(NOW.plusSeconds(60));
        assertThat(getTime(id, "sqs_published_at")).isNull();
        org.mockito.Mockito.clearInvocations(publisher);
        relay(NOW.plusSeconds(59)).publishPendingNotifications();
        verifyNoInteractions(publisher);
        given(publisher.publish(any()))
                .willReturn(PushPublicationResult.accepted("retry-accepted"));
        relay(NOW.plusSeconds(60)).publishPendingNotifications();
        assertThat(getStatus(id)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("권한·설정 등 영구 오류는 FAILED로 저장해 반복 발행하지 않는다")
    void publishPendingNotifications_permanentFailure_stopsPublication() {
        // Given
        UUID id = saveNotification(NOW, true);
        given(publisher.publish(any())).willReturn(PushPublicationResult.failed("AccessDenied"));

        // When
        relay(NOW).publishPendingNotifications();

        // Then
        assertThat(getStatus(id)).isEqualTo("FAILED");
        assertThat(getTime(id, "next_attempt_at")).isNull();
        assertThat(getTime(id, "sqs_published_at")).isNull();
        org.mockito.Mockito.clearInvocations(publisher);
        relay(NOW.plusSeconds(1)).publishPendingNotifications();
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("기한 전에는 발행하고 30분 정각부터는 큐에 보내지 않는다")
    void publishPendingNotifications_deadlineBoundary_enforcesOriginalDeadline(long seconds) {
        // Given
        UUID id = saveNotification(NOW, true);

        // When
        relay(NOW.plusSeconds(seconds)).publishPendingNotifications();

        // Then
        if (seconds < 1800) {
            assertThat(getStatus(id)).isEqualTo("PUBLISHED");
            return;
        }
        assertThat(getStatus(id)).isEqualTo("EXPIRED");
        assertThat(getTime(id, "next_attempt_at")).isNull();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("발행 불필요·발행 완료·재시도 미도래는 조회에서 제외한다")
    void publishPendingNotifications_ineligibleStates_doesNotPublish() {
        // Given
        UUID off = saveNotification(NOW, false);
        UUID published = saveNotification(NOW, true);
        UUID future = saveNotification(NOW, true);
        UUID failed = saveNotification(NOW, true);
        UUID expired = saveNotification(NOW, true);
        jdbcTemplate.update(
                "UPDATE notifications SET sqs_publish_status = 'PUBLISHED', next_attempt_at = NULL, sqs_published_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW), published);
        jdbcTemplate.update("UPDATE notifications SET next_attempt_at = ? WHERE id = ?",
                java.sql.Timestamp.from(NOW.plusSeconds(1)), future);
        jdbcTemplate.update(
                "UPDATE notifications SET sqs_publish_status = 'FAILED', next_attempt_at = NULL WHERE id = ?",
                failed);
        jdbcTemplate.update(
                "UPDATE notifications SET sqs_publish_status = 'EXPIRED', next_attempt_at = NULL WHERE id = ?",
                expired);

        // When
        relay(NOW).publishPendingNotifications();

        // Then
        verifyNoInteractions(publisher);
        assertThat(getStatus(off)).isEqualTo("NOT_REQUIRED");
        assertThat(getStatus(future)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("업무 트랜잭션에서 아직 커밋하지 않은 알림은 외부에 발행하지 않는다")
    void publishPendingNotifications_uncommittedNotification_doesNotPublish() {
        // Given & When
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            repository.save(Notification.approved(USER_ID, UUID.randomUUID(), UUID.randomUUID(),
                    NOW, true));
            entityManager.flush();
            relay(NOW).publishPendingNotifications();
            status.setRollbackOnly();
        });

        // Then
        verifyNoInteractions(publisher);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("한 작업의 DB 반영 실패는 다른 작업을 롤백하지 않고 다음 실행에서 동일 사건을 재발행한다")
    void publishPendingNotifications_databaseFailure_isolatesRollbackAndReusesEvent() {
        // Given
        UUID failedId = saveNotification(NOW, true);
        UUID successId = saveNotification(NOW.plusSeconds(1), true);
        jdbcTemplate.execute("""
                CREATE FUNCTION relay_reject_update() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF OLD.id = '%s'::uuid THEN RAISE EXCEPTION 'relay persistence failure'; END IF;
                    RETURN NEW;
                END $$
                """.formatted(failedId));
        jdbcTemplate.execute(
                "CREATE TRIGGER relay_reject_update BEFORE UPDATE ON notifications FOR EACH ROW EXECUTE FUNCTION relay_reject_update()");

        // When
        relay(NOW.plusSeconds(1)).publishPendingNotifications();

        // Then
        assertThat(getStatus(failedId)).isEqualTo("PENDING");
        assertThat(getTime(failedId, "sqs_published_at")).isNull();
        assertThat(getStatus(successId)).isEqualTo("PUBLISHED");
        jdbcTemplate.execute("DROP TRIGGER relay_reject_update ON notifications");
        relay(NOW.plusSeconds(2)).publishPendingNotifications();
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        org.mockito.Mockito.verify(publisher, org.mockito.Mockito.times(3))
                .publish(messages.capture());
        assertThat(messages.getAllValues().getFirst().eventId())
                .isEqualTo(messages.getAllValues().getLast().eventId());
        assertThat(getStatus(failedId)).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("같은 알림을 동시에 조회해도 잠긴 행은 기다리지 않고 건너뛴다")
    void publishPendingNotifications_concurrentRelays_publishesOnlyOnce() throws Exception {
        // Given
        UUID id = saveNotification(NOW, true);
        CountDownLatch publishing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        given(publisher.publish(any())).willAnswer(invocation -> {
            publishing.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test publication latch timed out");
            }
            return PushPublicationResult.accepted("only-once");
        });

        // When
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> relay(NOW).publishPendingNotifications());
            try {
                assertThat(publishing.await(5, TimeUnit.SECONDS)).isTrue();
                executor.submit(() -> relay(NOW).publishPendingNotifications()).get(3,
                        TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
            first.get(5, TimeUnit.SECONDS);
        }

        // Then
        assertThat(getStatus(id)).isEqualTo("PUBLISHED");
        org.mockito.Mockito.verify(publisher).publish(any());
    }

    @Test
    @DisplayName("대기 작업은 다음 시도 시각 순으로 정해진 크기만 조회한다")
    void findDuePublicationIds_orderAndLimit_returnsEarliestDueRows() {
        // Given
        UUID later = saveNotification(NOW.plusSeconds(1), true);
        UUID earlier = saveNotification(NOW, true);
        saveNotification(NOW.plusSeconds(2), true);

        // When & Then
        assertThat(repository.findDuePublicationIds(NOW.plusSeconds(2), 2)).containsExactly(earlier,
                later);
    }

    private NotificationRelayService relay(Instant now) {
        return new NotificationRelayService(repository, publisher, Clock.fixed(now, ZoneOffset.UTC),
                transactionManager);
    }

    private UUID saveNotification(Instant occurredAt, boolean pushEnabled) {
        return new TransactionTemplate(transactionManager).execute(status -> repository.save(
                Notification.approved(USER_ID, UUID.randomUUID(), UUID.randomUUID(), occurredAt,
                        pushEnabled))
                .getId());
    }

    private String getStatus(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT sqs_publish_status FROM notifications WHERE id = ?", String.class, id);
    }

    private Instant getTime(UUID id, String column) {
        return jdbcTemplate.queryForObject("SELECT " + column + " FROM notifications WHERE id = ?",
                (row, rowNumber) -> row.getTimestamp(1) == null
                        ? null
                        : row.getTimestamp(1).toInstant(),
                id);
    }
}
