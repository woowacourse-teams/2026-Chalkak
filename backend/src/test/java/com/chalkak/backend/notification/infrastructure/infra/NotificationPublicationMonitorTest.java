package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.PublishStatus;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PublicationBacklog;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NotificationPublicationMonitorTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID userId = UUID.randomUUID();
    private final Logger logger = (Logger) LoggerFactory
            .getLogger(NotificationPublicationMonitor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Autowired
    private NotificationRepository repository;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, 'monitor@example.com', CAST('ACTIVE' AS user_status), 'chalkak/signatures/test/original.webp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("발행 대기가 없으면 건수와 나이가 0인 정상 관측을 남긴다")
    void logBacklog_emptyPending_logsZero() {
        // Given
        entityManager.flush();
        entityManager.clear();

        // When
        new NotificationPublicationMonitor(repository, CLOCK).logBacklog();

        // Then
        assertThat(fields()).containsEntry("stage", "publication_snapshot")
                .containsEntry("pendingCount", 0L).containsEntry("oldestPendingAgeSeconds", 0L);
    }

    @Test
    @DisplayName("PENDING만 집계하며 재시도 대기와 기한이 지난 미처리 행도 포함한다")
    void logBacklog_mixedStates_countsOnlyPendingIncludingExpired() {
        // Given
        for (PublishStatus status : PublishStatus.values()) {
            Notification notification = Notification.approved(
                    userId,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    NOW.minusSeconds(status == PublishStatus.PENDING ? 2500 : 3000),
                    status != PublishStatus.NOT_REQUIRED
            );
            if (status == PublishStatus.PUBLISHED) {
                notification.markPublished(NOW);
            }
            if (status == PublishStatus.FAILED) {
                notification.failPublication();
            }
            if (status == PublishStatus.EXPIRED) {
                notification.expirePublication();
            }
            repository.save(notification);
        }
        Notification retrying = Notification.approved(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW.minusSeconds(20),
                true
        );
        retrying.retryPublication(NOW);
        repository.save(retrying);
        entityManager.flush();
        entityManager.clear();

        // When
        new NotificationPublicationMonitor(repository, CLOCK).logBacklog();

        // Then
        assertThat(fields()).containsEntry("pendingCount", 2L)
                .containsEntry("oldestPendingAgeSeconds", 2500L);
        PublicationBacklog backlog = repository.getPublicationBacklog();
        assertThat(backlog.oldestCreatedAt()).isEqualTo(NOW.minusSeconds(2500));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("생성 시각 직전·정각·직후에도 대기 나이가 음수가 되지 않는다")
    void logBacklog_creationBoundary_logsNonNegativeAge(long age) {
        // Given
        repository.save(Notification.approved(
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                NOW.minusSeconds(age),
                true
        ));
        entityManager.flush();
        entityManager.clear();

        // When
        new NotificationPublicationMonitor(repository, CLOCK).logBacklog();

        // Then
        assertThat(fields()).containsEntry("pendingCount", 1L)
                .containsEntry("oldestPendingAgeSeconds", Math.max(0, age));
    }

    @Test
    @DisplayName("조회 실패는 정상 0건으로 기록하지 않고 예외 원문 없이 오류를 기록한다")
    void logBacklog_queryFailure_logsErrorWithoutHealthySnapshot() {
        // Given
        NotificationRepository failingRepository = mock(NotificationRepository.class);
        given(failingRepository.getPublicationBacklog())
                .willThrow(new IllegalStateException("sensitive-database-detail"));

        // When
        new NotificationPublicationMonitor(failingRepository, CLOCK).logBacklog();

        // Then
        assertThat(fields()).containsEntry("stage", "publication_snapshot_failed")
                .containsEntry("errorCode", "IllegalStateException")
                .doesNotContainKeys("pendingCount", "oldestPendingAgeSeconds");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("sensitive");
        assertThat(appender.list.getFirst().getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("발행 대기 관측은 종료 후 지연 없이 시작 기준 60초 간격으로 예약한다")
    void logBacklog_schedule_usesFixedRate() throws NoSuchMethodException {
        // Given
        Scheduled scheduled = NotificationPublicationMonitor.class.getMethod("logBacklog")
                .getAnnotation(Scheduled.class);

        // When & Then
        assertThat(scheduled.fixedRate()).isEqualTo(60_000);
        assertThat(scheduled.fixedDelay()).isEqualTo(-1);
    }

    private Map<String, Object> fields() {
        assertThat(appender.list).hasSize(1);
        return appender.list.getFirst().getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
