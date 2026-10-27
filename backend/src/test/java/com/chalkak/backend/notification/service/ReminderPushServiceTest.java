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
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.notification.repository.ReminderSlice;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ReminderPushServiceTest extends IntegrationTestSupport {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-22T12:00:00Z");
    private static final UUID ACTIVE_USER = new UUID(0, 1);
    private static final Instant OPEN_ENDS_AT = Instant.parse("2026-10-22T15:00:00Z");
    private static final Instant CLOSED_ENDS_AT = Instant.parse("2026-10-22T11:00:00Z");

    @Autowired
    private ReminderRepository reminders;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private PlatformTransactionManager manager;
    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private PushMessagePublisher publisher;
    private FakeClock clock;
    private ReminderPushService service;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        insertUser(ACTIVE_USER, "ACTIVE");
        publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-message"));
        clock = new FakeClock(SCHEDULED_AT.plusSeconds(10));
        service = new ReminderPushService(
                reminders,
                topics,
                notifications,
                new DirectPushPublisher(publisher, clock),
                clock,
                manager
        );
        appender.start();
        ((Logger) LoggerFactory.getLogger(ReminderPushService.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(ReminderPushService.class)).detachAppender(appender);
        appender.stop();
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("전체 대상은 정상이면서 주제 알림을 켠 회원에게만 회차 값으로 발행하고 집계를 남긴다")
    void publishDueReminders_allTarget_publishesToTopicPushEnabledActiveUsers() {
        // Given
        insertUser(new UUID(0, 2), "ACTIVE");
        jdbc.update("UPDATE users SET topic_push_enabled = false WHERE id = ?", new UUID(0, 2));
        insertUser(new UUID(0, 3), "BANNED");
        insertWithdrawnUser(new UUID(0, 4));
        UUID reminderId = insertReminder("ONCE", "ALL");

        // When
        service.publishDueReminders();

        // Then
        PushMessage message = captureMessages(1).getFirst();
        assertThat(message.userId()).isEqualTo(ACTIVE_USER);
        assertThat(message.notificationId()).isNull();
        assertThat(message.type()).isEqualTo(NotificationType.REMINDER);
        assertThat(message.sourceType()).isEqualTo(NotificationSourceType.REMINDER);
        assertThat(message.sourceId()).isEqualTo(reminderId);
        assertThat(message.occurredAt()).isEqualTo(SCHEDULED_AT);
        assertThat(message.expiresAt()).isEqualTo(SCHEDULED_AT.plusSeconds(1800));
        assertThat(logsAtStage("reminder_publication_summary")).singleElement()
                .satisfies(log -> assertThat(log)
                        .containsEntry("reminderId", reminderId)
                        .containsEntry("eventId", message.eventId())
                        .containsEntry("targetCount", 1)
                        .containsEntry("acceptedCount", 1));
    }

    @Test
    @DisplayName("같은 회차는 매 분 다시 확인해도 한 번만 발행한다")
    void publishDueReminders_sameOccurrence_publishesOnce() {
        // Given
        insertReminder("DAILY", "ALL");

        // When
        service.publishDueReminders();
        clock.set(SCHEDULED_AT.plusSeconds(70));
        service.publishDueReminders();

        // Then
        verify(publisher, times(1)).publish(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ONCE", "DAILY"})
    @DisplayName("서비스를 다시 만들어 발행해도 일회성·매일 반복의 같은 회차는 같은 사건 ID를 사용한다")
    void publishDueReminders_restartedService_preservesEventIdAndDeadline(String repeat) {
        // Given
        insertReminder(repeat, "ALL");
        service.publishDueReminders();
        clock.set(SCHEDULED_AT.plusSeconds(70));
        ReminderPushService restarted = new ReminderPushService(
                reminders,
                topics,
                notifications,
                new DirectPushPublisher(publisher, clock),
                clock,
                manager
        );

        // When
        restarted.publishDueReminders();

        // Then
        List<PushMessage> messages = captureMessages(2);
        assertThat(messages.get(1)).isEqualTo(messages.getFirst());
    }

    @Test
    @DisplayName("같은 매일 반복 리마인더도 다음 날 회차는 다른 사건 ID를 사용한다")
    void publishDueReminders_nextDailyOccurrence_usesDifferentEventId() {
        // Given
        insertReminder("DAILY", "ALL");
        service.publishDueReminders();
        clock.set(SCHEDULED_AT.plusSeconds(86410));

        // When
        service.publishDueReminders();

        // Then
        List<PushMessage> messages = captureMessages(2);
        assertThat(messages).extracting(PushMessage::eventId).doesNotHaveDuplicates();
        assertThat(messages.get(1).sourceId()).isEqualTo(messages.getFirst().sourceId());
        assertThat(messages.get(1).occurredAt()).isEqualTo(SCHEDULED_AT.plusSeconds(86400));
        assertThat(messages.get(1).expiresAt()).isEqualTo(SCHEDULED_AT.plusSeconds(88200));
    }

    @Test
    @DisplayName("다른 요청이 리마인더 행을 잠그고 있으면 이번 확인은 건너뛰고 잠금이 풀린 뒤 다음 확인 때 발행한다")
    void publishDueReminders_lockedReminder_skipsAndPublishesNextMinute() throws Exception {
        // Given
        UUID reminderId = insertReminder("ONCE", "ALL");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        // When
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            Future<?> holder = executor.submit(() -> new TransactionTemplate(manager)
                    .executeWithoutResult(status -> {
                        jdbc.queryForObject(
                                "SELECT id FROM reminders WHERE id = ? FOR UPDATE",
                                UUID.class,
                                reminderId
                        );
                        locked.countDown();
                        awaitQuietly(release);
                    }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            service.publishDueReminders();
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        }
        verifyNoInteractions(publisher);
        clock.set(SCHEDULED_AT.plusSeconds(70));
        service.publishDueReminders();

        // Then
        verify(publisher, times(1)).publish(any());
        assertThat(logsAtStage("reminder_execution_skipped")).singleElement()
                .satisfies(log -> assertThat(log)
                        .containsEntry("reason", "REMINDER_LOCKED_OR_DELETED"));
    }

    @Test
    @DisplayName("목록을 읽은 뒤 발행 전에 삭제되거나 일정이 바뀐 리마인더는 발행하지 않는다")
    void publishDueReminders_changedBeforePublication_skips() {
        // Given
        UUID deleted = insertReminder("ONCE", "ALL");
        UUID rescheduled = insertReminder("ONCE", "ALL");
        ReminderRepository changingRepository = new ChangingReminderRepository(
                reminders,
                () -> {
                    updateTimestamp("UPDATE reminders SET deleted_at = ? WHERE id = ?", deleted);
                    updateTimestamp(
                            "UPDATE reminders SET scheduled_at = ? WHERE id = ?",
                            SCHEDULED_AT.plusSeconds(3600),
                            rescheduled
                    );
                }
        );
        service = new ReminderPushService(
                changingRepository,
                topics,
                notifications,
                new DirectPushPublisher(publisher, clock),
                clock,
                manager
        );

        // When
        service.publishDueReminders();

        // Then
        verifyNoInteractions(publisher);
        assertThat(logsAtStage("reminder_execution_skipped"))
                .extracting(log -> log.get("reason"))
                .containsExactlyInAnyOrder("REMINDER_LOCKED_OR_DELETED", "SCHEDULE_CHANGED");
    }

    @Test
    @DisplayName("오늘 주제 참여자 대상은 오늘 주제에 게시물을 올린 회원에게만 발행한다")
    void publishDueReminders_participantTarget_publishesToParticipants() {
        // Given
        UUID nonParticipant = new UUID(0, 2);
        insertUser(nonParticipant, "ACTIVE");
        UUID topicId = insertTopic(OPEN_ENDS_AT);
        insertApprovedPost(ACTIVE_USER, topicId);
        insertReminder("ONCE", "TOPIC_PARTICIPANTS");

        // When
        service.publishDueReminders();

        // Then
        assertThat(captureMessages(1)).extracting(PushMessage::userId).containsExactly(ACTIVE_USER);
    }

    @Test
    @DisplayName("오늘 주제 미참여자 대상은 오늘 주제에 아직 올리지 않은 회원에게만 발행한다")
    void publishDueReminders_nonParticipantTarget_publishesToNonParticipants() {
        // Given
        UUID nonParticipant = new UUID(0, 2);
        insertUser(nonParticipant, "ACTIVE");
        UUID topicId = insertTopic(OPEN_ENDS_AT);
        insertApprovedPost(ACTIVE_USER, topicId);
        insertReminder("ONCE", "TOPIC_NON_PARTICIPANTS");

        // When
        service.publishDueReminders();

        // Then
        assertThat(captureMessages(1)).extracting(PushMessage::userId)
                .containsExactly(nonParticipant);
    }

    @Test
    @DisplayName("오늘 주제 미참여자 대상은 오늘 주제가 마감된 뒤에도 아직 올리지 않은 회원에게 발행한다")
    void publishDueReminders_nonParticipantTargetAfterClose_publishes() {
        // Given
        UUID nonParticipant = new UUID(0, 2);
        insertUser(nonParticipant, "ACTIVE");
        UUID topicId = insertTopic(CLOSED_ENDS_AT);
        insertApprovedPost(ACTIVE_USER, topicId);
        insertReminder("ONCE", "TOPIC_NON_PARTICIPANTS");

        // When
        service.publishDueReminders();

        // Then
        assertThat(captureMessages(1)).extracting(PushMessage::userId)
                .containsExactly(nonParticipant);
    }

    @Test
    @DisplayName("오늘 주제 참여자 대상은 오늘 주제가 마감된 뒤에도 발행한다")
    void publishDueReminders_participantTargetAfterClose_publishes() {
        // Given
        UUID topicId = insertTopic(CLOSED_ENDS_AT);
        insertApprovedPost(ACTIVE_USER, topicId);
        insertReminder("ONCE", "TOPIC_PARTICIPANTS");

        // When
        service.publishDueReminders();

        // Then
        assertThat(captureMessages(1)).extracting(PushMessage::userId).containsExactly(ACTIVE_USER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC_PARTICIPANTS", "TOPIC_NON_PARTICIPANTS"})
    @DisplayName("오늘 주제가 없으면 주제 대상 리마인더는 발행하지 않고 한 번만 생략을 남긴다")
    void publishDueReminders_topicTargetWithoutTopic_skipsOnce(String target) {
        // Given
        insertReminder("ONCE", target);

        // When
        service.publishDueReminders();
        clock.set(SCHEDULED_AT.plusSeconds(70));
        service.publishDueReminders();

        // Then
        verifyNoInteractions(publisher);
        assertThat(logsAtStage("reminder_execution_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", "NO_TOPIC"));
    }

    @Test
    @DisplayName("자정을 넘겨 발행한 일회성 회차도 회차 날짜의 주제로 대상을 고른다")
    void publishDueReminders_onceAfterKstMidnight_usesOccurrenceDateTopic() {
        // Given
        Instant scheduledAt = Instant.parse("2026-10-22T14:59:30Z");
        UUID topicId = insertTopic(OPEN_ENDS_AT);
        insertApprovedPost(ACTIVE_USER, topicId);
        UUID reminderId = insertReminder("ONCE", "TOPIC_PARTICIPANTS");
        updateTimestamp(
                "UPDATE reminders SET scheduled_at = ? WHERE id = ?",
                scheduledAt,
                reminderId
        );
        clock.set(Instant.parse("2026-10-22T15:00:00Z"));

        // When
        service.publishDueReminders();

        // Then
        PushMessage message = captureMessages(1).getFirst();
        assertThat(message.userId()).isEqualTo(ACTIVE_USER);
        assertThat(message.occurredAt()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("예정 시각부터 30분이 지난 회차는 발행하지 않는다")
    void publishDueReminders_expiredOccurrence_doesNotPublish() {
        // Given
        insertReminder("ONCE", "ALL");
        clock.set(SCHEDULED_AT.plusSeconds(1800));

        // When
        service.publishDueReminders();

        // Then
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("삭제된 리마인더의 시도 기록을 지워, 같은 ID가 다시 나타나도 회차를 새로 판단한다")
    void publishDueReminders_deletedReminder_forgetsAttempt() {
        // Given
        UUID reminderId = insertReminder("DAILY", "ALL");
        service.publishDueReminders();
        updateTimestamp("UPDATE reminders SET deleted_at = ? WHERE id = ?", reminderId);
        clock.set(SCHEDULED_AT.plusSeconds(70));
        service.publishDueReminders();

        // When
        jdbc.update("UPDATE reminders SET deleted_at = NULL WHERE id = ?", reminderId);
        clock.set(SCHEDULED_AT.plusSeconds(130));
        service.publishDueReminders();

        // Then
        verify(publisher, times(2)).publish(any());
    }

    private UUID insertReminder(String repeat, String target) {
        return jdbc.queryForObject(
                """
                        INSERT INTO reminders (title, body, repeat, target, scheduled_at, created_at, updated_at)
                        VALUES ('오늘이 얼마 남지 않았어요', '사진을 자랑해 주세요!', ?, ?, ?,
                            '2026-10-21T00:00:00Z', '2026-10-21T00:00:00Z')
                        RETURNING id
                        """,
                UUID.class,
                repeat,
                target,
                Timestamp.from(SCHEDULED_AT)
        );
    }

    private void insertUser(UUID id, String status) {
        jdbc.update(
                """
                        INSERT INTO users(id,email,status,signature_original_storage_key)
                        VALUES (?,?,CAST(? AS user_status),?)
                        """,
                id,
                id + "@reminder-test.example",
                status,
                "reminder/signature/" + id
        );
    }

    // 회차 날짜(KST 2026-10-22)의 주제다. 시작은 KST 자정이고 마감 시각만 바꿔 열림·마감 상태를 만든다.
    private UUID insertTopic(Instant endsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?,'빛과 그림자',?,?,?)",
                id,
                Date.valueOf("2026-10-22"),
                Timestamp.from(Instant.parse("2026-10-21T15:00:00Z")),
                Timestamp.from(endsAt)
        );
        return id;
    }

    private void insertApprovedPost(UUID userId, UUID topicId) {
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
        jdbc.update(
                """
                        INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status)
                        VALUES (?,?,?,?,'APPROVED')
                        """,
                UUID.randomUUID(),
                userId,
                topicId,
                photoId
        );
    }

    private void insertWithdrawnUser(UUID id) {
        insertUser(id, "ACTIVE");
        updateTimestamp("UPDATE users SET deleted_at = ? WHERE id = ?", id);
    }

    private void updateTimestamp(String sql, UUID id) {
        updateTimestamp(
                sql,
                SCHEDULED_AT,
                id
        );
    }

    private void updateTimestamp(
            String sql,
            Instant value,
            UUID id
    ) {
        jdbc.update(
                sql,
                Timestamp.from(value),
                id
        );
    }

    private List<PushMessage> captureMessages(int count) {
        ArgumentCaptor<PushMessage> messages = ArgumentCaptor.forClass(PushMessage.class);
        verify(publisher, times(count)).publish(messages.capture());
        return messages.getAllValues();
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

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    // 목록 조회 뒤 발행 직전 확인 전에 다른 요청이 리마인더를 바꾼 상황을 만든다.
    private static class ChangingReminderRepository implements ReminderRepository {

        private final ReminderRepository delegate;
        private final Runnable change;
        private boolean changed;

        ChangingReminderRepository(ReminderRepository delegate, Runnable change) {
            this.delegate = delegate;
            this.change = change;
        }

        @Override
        public List<Reminder> findActiveReminders() {
            return delegate.findActiveReminders();
        }

        @Override
        public Optional<Reminder> findActiveById(UUID reminderId) {
            return delegate.findActiveById(reminderId);
        }

        @Override
        public Optional<Reminder> findActiveForPublication(UUID reminderId) {
            if (!changed) {
                change.run();
                changed = true;
            }
            return delegate.findActiveForPublication(reminderId);
        }

        @Override
        public Reminder save(
                Reminder reminder) {
            return delegate.save(reminder);
        }

        @Override
        public Reminder saveAndFlush(
                Reminder reminder) {
            return delegate.saveAndFlush(reminder);
        }

        @Override
        public Optional<Reminder> findByIdForUpdate(
                UUID reminderId) {
            return delegate.findByIdForUpdate(reminderId);
        }

        @Override
        public ReminderSlice findReminders(
                int page,
                int pageSize
        ) {
            return delegate.findReminders(page, pageSize);
        }
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
