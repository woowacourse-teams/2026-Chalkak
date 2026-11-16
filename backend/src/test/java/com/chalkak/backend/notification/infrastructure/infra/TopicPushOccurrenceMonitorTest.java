package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushEventIdPolicy;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

class TopicPushOccurrenceMonitorTest {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-11-14T14:59:00Z");
    private static final Instant GRACE_END = SCHEDULED_AT.plusSeconds(300);
    private static final LocalDate TOPIC_DATE = LocalDate.parse("2026-11-14");
    private static final UUID SCHEDULE_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = PushEventIdPolicy.createForOccurrence(
            NotificationType.TOPIC,
            SCHEDULE_ID,
            SCHEDULED_AT
    );

    private final Logger logger = (Logger) LoggerFactory
            .getLogger(TopicPushOccurrenceMonitor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final TopicPushScheduleRepository schedules = mock(TopicPushScheduleRepository.class);
    private final TopicRepository topics = mock(TopicRepository.class);
    private final CloudWatchTopicPushResults results = mock(CloudWatchTopicPushResults.class);

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        given(topics.findActiveByTopicDate(TOPIC_DATE)).willReturn(Optional.of(mock(Topic.class)));
        scheduleUpdatedAt(SCHEDULED_AT.minusSeconds(60));
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("5분 직전에는 예정 회차 결과를 조회하지 않고 누락 0건을 기록한다")
    void logMissingOccurrences_beforeGracePeriod_logsZero() {
        // When
        monitorAt(GRACE_END.minusMillis(1)).logMissingOccurrences();

        // Then
        verifyNoInteractions(results);
        assertThat(values("missingCount")).containsExactly(0L);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 1})
    @DisplayName("KST 자정을 넘겨도 5분 경계와 직후에는 전날 회차 누락을 기록한다")
    void logMissingOccurrences_afterMidnightWithoutResult_logsMissing(long millis) {
        // Given
        Instant now = GRACE_END.plusMillis(millis);
        given(results.findRecordedEventIds(SCHEDULED_AT, now)).willReturn(Set.of());

        // When
        monitorAt(now).logMissingOccurrences();

        // Then
        assertThat(values("eventId")).containsExactly(EVENT_ID);
        assertThat(values("occurredAt")).containsExactly(SCHEDULED_AT);
        assertThat(values("missingCount")).containsExactly(1L);
    }

    @Test
    @DisplayName("동일 회차의 결과가 있으면 누락 0건을 기록한다")
    void logMissingOccurrences_withRecordedResult_logsZero() {
        // Given
        given(results.findRecordedEventIds(SCHEDULED_AT, GRACE_END))
                .willReturn(Set.of(EVENT_ID.toString()));

        // When
        monitorAt(GRACE_END).logMissingOccurrences();

        // Then
        assertThat(values("missingCount")).containsExactly(0L);
    }

    @Test
    @DisplayName("다른 회차의 결과가 있어도 현재 회차 누락을 기록한다")
    void logMissingOccurrences_withUnrelatedResult_logsMissing() {
        // Given
        given(results.findRecordedEventIds(SCHEDULED_AT, GRACE_END))
                .willReturn(Set.of(UUID.randomUUID().toString()));

        // When
        monitorAt(GRACE_END).logMissingOccurrences();

        // Then
        assertThat(values("missingCount")).containsExactly(1L);
    }

    @Test
    @DisplayName("로그 조회에 실패하면 정상 관측값 없이 조회 실패를 기록한다")
    void logMissingOccurrences_withUnavailableLogs_logsObservationFailure() {
        // Given
        given(results.findRecordedEventIds(SCHEDULED_AT, GRACE_END))
                .willThrow(new IllegalStateException());

        // When
        monitorAt(GRACE_END).logMissingOccurrences();

        // Then
        assertThat(values("stage")).containsExactly("topic_monitor_failed");
        assertThat(values("missingCount")).isEmpty();
    }

    @Test
    @DisplayName("예약 시각에 수정된 설정의 이전 회차는 감시하지 않는다")
    void logMissingOccurrences_withChangedSchedule_logsZero() {
        // Given
        scheduleUpdatedAt(SCHEDULED_AT);

        // When
        monitorAt(GRACE_END).logMissingOccurrences();

        // Then
        verifyNoInteractions(results);
        assertThat(values("missingCount")).containsExactly(0L);
    }

    @Test
    @DisplayName("활성 주제가 없는 날에는 예약 누락을 집계하지 않는다")
    void logMissingOccurrences_withoutActiveTopic_logsZero() {
        // Given
        given(topics.findActiveByTopicDate(TOPIC_DATE)).willReturn(Optional.empty());

        // When
        monitorAt(GRACE_END).logMissingOccurrences();

        // Then
        verifyNoInteractions(results);
        assertThat(values("missingCount")).containsExactly(0L);
    }

    @ParameterizedTest
    @ValueSource(longs = {1800, 1801})
    @DisplayName("발송 기한과 그 이후의 회차는 감시 대상에서 제외한다")
    void logMissingOccurrences_withExpiredOccurrence_logsZero(long seconds) {
        // When
        monitorAt(SCHEDULED_AT.plusSeconds(seconds)).logMissingOccurrences();

        // Then
        verifyNoInteractions(results);
        assertThat(values("missingCount")).containsExactly(0L);
    }

    private TopicPushOccurrenceMonitor monitorAt(Instant now) {
        return new TopicPushOccurrenceMonitor(
                schedules,
                topics,
                results,
                Clock.fixed(now, ZoneOffset.UTC)
        );
    }

    private void scheduleUpdatedAt(Instant updatedAt) {
        TopicPushSchedule schedule = new TopicPushSchedule(LocalTime.of(23, 59));
        ReflectionTestUtils.setField(
                schedule,
                "id",
                SCHEDULE_ID
        );
        ReflectionTestUtils.setField(
                schedule,
                "updatedAt",
                updatedAt
        );
        given(schedules.findAllByOrderBySendTimeAsc()).willReturn(List.of(schedule));
    }

    private List<Object> values(String key) {
        return appender.list.stream().flatMap(event -> event.getKeyValuePairs().stream())
                .filter(field -> field.key.equals(key))
                .map(field -> field.value)
                .toList();
    }
}
