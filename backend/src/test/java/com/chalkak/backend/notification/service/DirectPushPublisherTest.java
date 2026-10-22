package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class DirectPushPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-22T12:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-22T12:30:00Z");
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID SOURCE_ID = UUID.randomUUID();
    private static final DirectPushOccurrence OCCURRENCE = new DirectPushOccurrence(
            EVENT_ID,
            NotificationType.TOPIC,
            NotificationSourceType.TOPIC,
            SOURCE_ID,
            OCCURRED_AT,
            "sample"
    );

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private PushMessagePublisher publisher;
    private FakeClock clock;
    private DirectPushPublisher directPushPublisher;

    @BeforeEach
    void setUp() {
        publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-message"));
        clock = new FakeClock(OCCURRED_AT);
        directPushPublisher = new DirectPushPublisher(publisher, clock);
        appender.start();
        ((Logger) LoggerFactory.getLogger(DirectPushPublisher.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(DirectPushPublisher.class)).detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("회원마다 회차의 공통 값과 예정 시각부터 30분 뒤의 만료 시각을 담아 발행하고 수락을 센다")
    void publish_acceptedMessages_buildsMessagesFromOccurrence() {
        // Given
        List<UUID> userIds = List.of(new UUID(0, 1), new UUID(0, 2));

        // When
        DirectPublicationSummary summary = directPushPublisher.publish(OCCURRENCE, userIds);

        // Then
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(publisher, times(2)).publish(messages.capture());
        assertThat(messages.getAllValues()).extracting(PushMessage::userId)
                .containsExactlyElementsOf(userIds);
        assertThat(messages.getAllValues()).allSatisfy(message -> {
            assertThat(message.eventId()).isEqualTo(EVENT_ID);
            assertThat(message.notificationId()).isNull();
            assertThat(message.type()).isEqualTo(NotificationType.TOPIC);
            assertThat(message.sourceType()).isEqualTo(NotificationSourceType.TOPIC);
            assertThat(message.sourceId()).isEqualTo(SOURCE_ID);
            assertThat(message.occurredAt()).isEqualTo(OCCURRED_AT);
            assertThat(message.expiresAt()).isEqualTo(EXPIRES_AT);
        });
        assertThat(summary).isEqualTo(new DirectPublicationSummary(
                EXPIRES_AT,
                2,
                2,
                0,
                0,
                0
        ));
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("실패와 결과 불명을 구분해 세고 접두사가 붙은 발행 결과 로그를 남긴다")
    void publish_failedAndUnknownResults_countsAndLogsWithPrefix() {
        // Given
        given(publisher.publish(any())).willReturn(
                PushPublicationResult.retry("RequestThrottled"),
                PushPublicationResult.unknown("SQS_CLIENT_ERROR")
        );

        // When
        DirectPublicationSummary summary = directPushPublisher.publish(
                OCCURRENCE,
                List.of(new UUID(0, 1), new UUID(0, 2))
        );

        // Then
        assertThat(summary.failedCount()).isEqualTo(1);
        assertThat(summary.unknownCount()).isEqualTo(1);
        List<Map<String, Object>> results = logsAtStage("sample_publication_result");
        assertThat(results).hasSize(2);
        assertThat(results.getFirst()).containsEntry("userId", new UUID(0, 1))
                .containsEntry("result", "FAILED")
                .containsEntry("errorCode", "RequestThrottled");
        assertThat(results.get(1)).containsEntry("userId", new UUID(0, 2))
                .containsEntry("result", "UNKNOWN")
                .containsEntry("errorCode", "SQS_CLIENT_ERROR");
        assertThat(results).allSatisfy(log -> assertThat(log)
                .containsEntry("type", "notification")
                .containsEntry("eventId", EVENT_ID)
                .containsEntry("sourceId", SOURCE_ID)
                .containsEntry("notificationType", NotificationType.TOPIC));
    }

    @Test
    @DisplayName("발행 중 예외는 결과 불명으로 세고 접두사가 붙은 확인 실패 로그를 남긴 뒤 다음 회원을 계속 발행한다")
    void publish_publisherThrows_countsUnknownAndContinues() {
        // Given
        given(publisher.publish(any()))
                .willThrow(new IllegalStateException("private-message-must-not-be-logged"))
                .willReturn(PushPublicationResult.accepted("sqs-message"));

        // When
        DirectPublicationSummary summary = directPushPublisher.publish(
                OCCURRENCE,
                List.of(new UUID(0, 1), new UUID(0, 2))
        );

        // Then
        verify(publisher, times(2)).publish(any());
        assertThat(summary.unknownCount()).isEqualTo(1);
        assertThat(summary.acceptedCount()).isEqualTo(1);
        assertThat(logsAtStage("sample_publication_unknown")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("userId", new UUID(0, 1))
                        .containsEntry("result", "UNKNOWN")
                        .containsEntry("errorCode", "IllegalStateException"));
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .noneMatch(message -> message.contains("private-message-must-not-be-logged"));
    }

    @Test
    @DisplayName("발행 도중 기한에 도달하면 남은 회원은 발행하지 않고 기한 생략으로 센다")
    void publish_deadlineDuringPublication_skipsRemainingRecipients() {
        // Given
        given(publisher.publish(any())).willAnswer(call -> {
            clock.set(EXPIRES_AT);
            return PushPublicationResult.accepted("sqs-message");
        });

        // When
        DirectPublicationSummary summary = directPushPublisher.publish(
                OCCURRENCE,
                List.of(
                        new UUID(0, 1),
                        new UUID(0, 2),
                        new UUID(0, 3)
                )
        );

        // Then
        verify(publisher, times(1)).publish(any());
        assertThat(summary.acceptedCount()).isEqualTo(1);
        assertThat(summary.expiredCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("시작 시점이 기한 직전이면 발행하고 기한 정각이면 아무도 발행하지 않는다")
    void publish_startAtDeadlineBoundary_publishesOnlyBeforeDeadline() {
        // Given
        clock.set(EXPIRES_AT.minusNanos(1));

        // When
        DirectPublicationSummary beforeDeadline = directPushPublisher.publish(
                OCCURRENCE,
                List.of(new UUID(0, 1))
        );
        clock.set(EXPIRES_AT);
        DirectPublicationSummary atDeadline = directPushPublisher.publish(
                OCCURRENCE,
                List.of(new UUID(0, 1))
        );

        // Then
        verify(publisher, times(1)).publish(any());
        assertThat(beforeDeadline.acceptedCount()).isEqualTo(1);
        assertThat(atDeadline.acceptedCount()).isZero();
        assertThat(atDeadline.expiredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("회원이 없으면 발행하지 않고 모든 건수를 0으로 센다")
    void publish_noRecipients_returnsEmptySummary() {
        // When
        DirectPublicationSummary summary = directPushPublisher.publish(OCCURRENCE, List.of());

        // Then
        verifyNoInteractions(publisher);
        assertThat(summary).isEqualTo(new DirectPublicationSummary(
                EXPIRES_AT,
                0,
                0,
                0,
                0,
                0
        ));
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            Map<String, Object> values = new HashMap<>();
            for (var entry : event.getKeyValuePairs()) {
                values.put(entry.key, entry.value);
            }
            if (stage.equals(values.get("stage"))) {
                logs.add(values);
            }
        }
        return logs;
    }

    private static class FakeClock extends Clock {
        private Instant now;

        FakeClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
