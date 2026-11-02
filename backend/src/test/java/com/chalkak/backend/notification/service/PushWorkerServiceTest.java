package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class PushWorkerServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final UUID EVENT_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657ab1");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657ab2");
    private static final UUID SOURCE_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657ab3");

    @Mock
    private NotificationRepository notifications;
    @Mock
    private TopicRepository topics;
    @Mock
    private PostRepository posts;
    @Mock
    private ReminderRepository reminders;
    @Mock
    private PushDeviceRepository devices;
    @Mock
    private LoginSessionService sessions;
    @Mock
    private DevicePushSender sender;
    @Mock
    private PlatformTransactionManager transactions;
    @Mock
    private PushDeliveryService delivery;

    private PushWorkerService worker;
    private final Logger logger = (Logger) LoggerFactory.getLogger(PushWorkerService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        worker = new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactions,
                delivery
        );
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @MethodSource("invalidDirectMessages")
    @DisplayName("잘못된 주제와 리마인더 메시지는 DB와 발송을 시작하지 않고 무시한다")
    void process_invalidDirectMessage_completesWithoutDatabaseOrSending(
            NotificationType type,
            String invalidField
    ) {
        // Given
        NotificationSourceType sourceType = type == NotificationType.TOPIC
                ? NotificationSourceType.TOPIC
                : NotificationSourceType.REMINDER;
        Instant occurredAt = invalidField.equals("future") ? NOW.plusSeconds(60) : NOW;
        PushMessage invalid = new PushMessage(
                invalidField.equals("eventId") ? null : EVENT_ID,
                invalidField.equals("notificationId") ? SOURCE_ID : null,
                USER_ID,
                type,
                invalidField.equals("sourceType") ? NotificationSourceType.POST : sourceType,
                SOURCE_ID,
                occurredAt,
                occurredAt.plusSeconds(invalidField.equals("deadline") ? 3600 : 1800)
        );

        // When & Then
        assertSkipped(invalid, "INVALID_MESSAGE");
    }

    private static Stream<Arguments> invalidDirectMessages() {
        return Stream.of(NotificationType.TOPIC, NotificationType.REMINDER)
                .flatMap(type -> Stream.of(
                        "notificationId",
                        "sourceType",
                        "deadline",
                        "future",
                        "eventId"
                )
                        .map(field -> Arguments.of(type, field)));
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"TOPIC", "POST_APPROVED"})
    @DisplayName("만료된 직접 발행과 알림함 메시지는 DB와 발송을 시작하지 않는다")
    void process_expiredMessage_completesWithoutDatabaseOrSending(NotificationType type) {
        // Given
        boolean direct = type == NotificationType.TOPIC;
        NotificationSourceType sourceType = direct
                ? NotificationSourceType.TOPIC
                : NotificationSourceType.POST;
        PushMessage expired = new PushMessage(
                EVENT_ID,
                direct ? null : SOURCE_ID,
                USER_ID,
                type,
                sourceType,
                SOURCE_ID,
                NOW.minusSeconds(1800),
                NOW
        );

        // When & Then
        assertSkipped(expired, "EXPIRED");
    }

    @Test
    @DisplayName("메시지가 없으면 DB와 발송을 시작하지 않고 무시한다")
    void process_nullMessage_completesWithoutDatabaseOrSending() {
        // When & Then
        assertSkipped(null, "INVALID_MESSAGE");
    }

    private void assertSkipped(PushMessage message, String reason) {
        assertThat(worker.process(message)).isEqualTo(PushProcessingResult.completed());
        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(event.getKeyValuePairs())
                        .anySatisfy(pair -> {
                            assertThat(pair.key).isEqualTo("reason");
                            assertThat(pair.value).isEqualTo(reason);
                        }));
        verifyNoInteractions(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                transactions,
                delivery
        );
    }
}
