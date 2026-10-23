package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class PushWorkerReminderTest extends IntegrationTestSupport {

    // KST 2026-10-22 21:00
    private static final Instant OCCURRED_AT = Instant.parse("2026-10-22T12:00:00Z");
    private static final Instant NOW = OCCURRED_AT.plusSeconds(60);
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TITLE = "오늘이 얼마 남지 않았어요";
    private static final String BODY = "사진을 자랑해 주세요!";

    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private TopicRepository topics;
    @Autowired
    private PostRepository posts;
    @Autowired
    private ReminderRepository reminders;
    @Autowired
    private PushDeviceRepository devices;
    @Autowired
    private LoginSessionService sessions;
    @Autowired
    private PlatformTransactionManager manager;
    @Autowired
    private JdbcTemplate jdbc;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private DevicePushSender sender;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update(
                "INSERT INTO users(id,email,status,signature_original_storage_key) VALUES (?, 'reminder-worker@example.com', 'ACTIVE', 'reminder/signature')",
                USER_ID);
        addDevice("reminder-token");
        sender = mock(DevicePushSender.class);
        given(sender.send(any(), any())).willReturn(DevicePushResult.accepted());
        appender.start();
        ((Logger) LoggerFactory.getLogger(PushWorkerService.class)).addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(PushWorkerService.class)).detachAppender(appender);
        appender.stop();
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("전체 대상 리마인더는 리마인더의 제목·본문으로 현재 기기에 보내고 알림함에 저장하지 않는다")
    void process_allTarget_sendsReminderWithoutInbox() {
        // Given
        PushMessage message = reminderMessage(insertReminder("ALL"));

        // When
        PushProcessingResult result = worker().process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        ArgumentCaptor<DevicePushRequest> request = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender).send(request.capture(), any());
        assertThat(request.getValue().message()).isEqualTo(message);
        assertThat(request.getValue().title()).isEqualTo(TITLE);
        assertThat(request.getValue().body()).isEqualTo(BODY);
        assertThat(request.getValue().token()).isEqualTo("reminder-token");
        assertThat(request.getValue().expiresAt()).isEqualTo(OCCURRED_AT.plusSeconds(1800));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("발행 뒤 고친 문구가 있으면 발송 직전의 문구로 보낸다")
    void process_editedAfterPublication_sendsCurrentText() {
        // Given
        UUID reminderId = insertReminder("ALL");
        jdbc.update("UPDATE reminders SET title = '고친 제목', body = '고친 본문' WHERE id = ?",
                reminderId);

        // When
        worker().process(reminderMessage(reminderId));

        // Then
        ArgumentCaptor<DevicePushRequest> request = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender).send(request.capture(), any());
        assertThat(request.getValue().title()).isEqualTo("고친 제목");
        assertThat(request.getValue().body()).isEqualTo("고친 본문");
    }

    @Test
    @DisplayName("주제 알림 수신을 끈 회원에게는 보내지 않는다")
    void process_topicPushDisabled_skips() {
        // Given
        jdbc.update("UPDATE users SET topic_push_enabled = false WHERE id = ?", USER_ID);

        // When & Then
        assertSkipped(reminderMessage(insertReminder("ALL")), "PUSH_DISABLED");
    }

    @Test
    @DisplayName("정지된 회원에게는 보내지 않는다")
    void process_bannedUser_skips() {
        // Given
        jdbc.update("UPDATE users SET status = 'BANNED' WHERE id = ?", USER_ID);

        // When & Then
        assertSkipped(reminderMessage(insertReminder("ALL")), "USER_INACTIVE");
    }

    @Test
    @DisplayName("발행 뒤 삭제된 리마인더는 보내지 않는다")
    void process_deletedAfterPublication_skips() {
        // Given
        UUID reminderId = insertReminder("ALL");
        jdbc.update(
                "UPDATE reminders SET deleted_at = ? WHERE id = ?",
                Timestamp.from(OCCURRED_AT),
                reminderId
        );

        // When & Then
        assertSkipped(reminderMessage(reminderId), "REMINDER_UNAVAILABLE");
    }

    @Test
    @DisplayName("참여자 대상은 오늘 주제에 올린 회원에게 보내고 올리지 않은 회원은 생략한다")
    void process_participantTarget_sendsOnlyToParticipant() {
        // Given
        UUID topicId = insertTopic();
        PushMessage message = reminderMessage(insertReminder("TOPIC_PARTICIPANTS"));
        assertSkipped(message, "NOT_PARTICIPATED");
        insertPost(
                topicId,
                ModerationStatus.APPROVED,
                false
        );

        // When
        worker().process(message);

        // Then
        verify(sender).send(any(), any());
    }

    @ParameterizedTest
    @CsvSource({
            "APPROVED, false, false", "PENDING, false, false", "VALIDATING, false, false",
            "REJECTED, false, true", "APPROVED, true, true"
    })
    @DisplayName("미참여자 대상은 그 사이 올렸으면 생략하고 반려·삭제만 있으면 보낸다")
    void process_nonParticipantTarget_rechecksParticipation(
            ModerationStatus status,
            boolean deleted,
            boolean sendable
    ) {
        // Given
        UUID topicId = insertTopic();
        insertPost(
                topicId,
                status,
                deleted
        );
        PushMessage message = reminderMessage(insertReminder("TOPIC_NON_PARTICIPANTS"));

        // When & Then
        if (sendable) {
            worker().process(message);
            verify(sender).send(any(), any());
            return;
        }
        assertSkipped(message, "ALREADY_PARTICIPATED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TOPIC_PARTICIPANTS", "TOPIC_NON_PARTICIPANTS"})
    @DisplayName("주제 대상인데 회차 날짜의 주제가 없으면 보내지 않는다")
    void process_topicTargetWithoutTopic_skips(String target) {
        // When & Then
        assertSkipped(reminderMessage(insertReminder(target)), "TOPIC_UNAVAILABLE");
    }

    @Test
    @DisplayName("미참여자 대상은 오늘 주제의 참여 시간이 끝난 뒤에도 보낸다")
    void process_nonParticipantTargetAfterClose_sends() {
        // Given
        UUID topicId = insertTopic();
        jdbc.update(
                "UPDATE topics SET ends_at = ? WHERE id = ?",
                Timestamp.from(OCCURRED_AT.minusSeconds(3600)),
                topicId
        );
        PushMessage message = reminderMessage(insertReminder("TOPIC_NON_PARTICIPANTS"));

        // When
        worker().process(message);

        // Then
        verify(sender).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"notificationId", "sourceType", "deadline", "future"})
    @DisplayName("알림 ID·다른 대상 종류·30분이 아닌 기한·미래 예정 시각이 있는 리마인더 메시지는 보내지 않는다")
    void process_invalidReminderMessage_skips(String invalidField) {
        // Given
        PushMessage valid = reminderMessage(insertReminder("ALL"));
        UUID notificationId = null;
        NotificationSourceType sourceType = NotificationSourceType.REMINDER;
        Instant occurredAt = valid.occurredAt();
        Instant expiresAt = valid.expiresAt();
        if (invalidField.equals("notificationId")) {
            notificationId = UUID.randomUUID();
        }
        if (invalidField.equals("sourceType")) {
            sourceType = NotificationSourceType.TOPIC;
        }
        if (invalidField.equals("deadline")) {
            expiresAt = occurredAt.plusSeconds(3600);
        }
        if (invalidField.equals("future")) {
            occurredAt = NOW.plusSeconds(60);
            expiresAt = occurredAt.plusSeconds(1800);
        }
        PushMessage invalid = new PushMessage(
                valid.eventId(),
                notificationId,
                USER_ID,
                NotificationType.REMINDER,
                sourceType,
                valid.sourceId(),
                occurredAt,
                expiresAt
        );

        // When & Then
        assertSkipped(invalid, "INVALID_MESSAGE");
    }

    private void assertSkipped(PushMessage message, String reason) {
        appender.list.clear();
        assertThat(worker().process(message)).isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
        assertThat(logsAtStage("worker_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", reason));
    }

    private PushMessage reminderMessage(UUID reminderId) {
        return new PushMessage(
                UUID.randomUUID(),
                null,
                USER_ID,
                NotificationType.REMINDER,
                NotificationSourceType.REMINDER,
                reminderId,
                OCCURRED_AT,
                OCCURRED_AT.plusSeconds(1800)
        );
    }

    private UUID insertReminder(String target) {
        return jdbc.queryForObject(
                """
                        INSERT INTO reminders (title, body, repeat, target, scheduled_at, created_at, updated_at)
                        VALUES (?, ?, 'ONCE', ?, ?, '2026-10-21T00:00:00Z', '2026-10-21T00:00:00Z')
                        RETURNING id
                        """,
                UUID.class,
                TITLE,
                BODY,
                target,
                Timestamp.from(OCCURRED_AT)
        );
    }

    // 회차 날짜(KST 2026-10-22)의 주제다.
    private UUID insertTopic() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?,'빛과 그림자',?,?,?)",
                id,
                Date.valueOf("2026-10-22"),
                Timestamp.from(Instant.parse("2026-10-21T15:00:00Z")),
                Timestamp.from(Instant.parse("2026-10-22T15:00:00Z"))
        );
        return id;
    }

    private void insertPost(
            UUID topicId,
            ModerationStatus status,
            boolean deleted
    ) {
        UUID photoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO photos(id,original_storage_key) VALUES (?,?)",
                photoId,
                "reminder/photo/" + photoId
        );
        Timestamp deletedAt = null;
        if (deleted) {
            deletedAt = Timestamp.from(OCCURRED_AT.plusSeconds(30));
        }
        jdbc.update(
                """
                        INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status,deleted_at,created_at)
                        VALUES (?,?,?,?,CAST(? AS moderation_status),?,?)
                        """,
                UUID.randomUUID(),
                USER_ID,
                topicId,
                photoId,
                status.name(),
                deletedAt,
                Timestamp.from(OCCURRED_AT.plusSeconds(10))
        );
    }

    private void addDevice(String token) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO user_refresh_tokens(user_id,session_id,token_hash,expires_at,absolute_expires_at) VALUES (?,?,?,?,?)",
                USER_ID,
                sessionId,
                new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(7200))
        );
        jdbc.update(
                "INSERT INTO push_devices(user_id,session_id,fcm_token,fcm_token_hash,registered_at,updated_at) VALUES (?,?,?,?,?,?)",
                USER_ID,
                sessionId,
                token,
                new FcmToken(token).getHash(),
                Timestamp.from(OCCURRED_AT.minusSeconds(60)),
                Timestamp.from(OCCURRED_AT.minusSeconds(60))
        );
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        List<Map<String, Object>> logs = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            Map<String, Object> values = new HashMap<>();
            event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
            if (stage.equals(values.get("stage"))) {
                logs.add(values);
            }
        }
        return logs;
    }

    private PushWorkerService worker() {
        return new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                manager
        );
    }
}
