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
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.repository.TopicRepository;
import com.chalkak.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

class PushWorkerNoticeTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TITLE = "서비스 점검 안내";
    private static final String BODY = "10월 20일 02:00부터 점검합니다.";

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NoticeRepository notices;

    @Autowired
    private PushDeviceRepository devices;

    @Autowired
    private UserRepository users;

    @Autowired
    private TopicRepository topics;

    @Autowired
    private PostRepository posts;

    @Autowired
    private ReminderRepository reminders;

    @Autowired
    private LoginSessionService sessions;

    @Autowired
    private PlatformTransactionManager manager;

    @Autowired
    private JdbcTemplate jdbc;

    private final Logger logger = (Logger) LoggerFactory.getLogger(PushWorkerService.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private PushMessage message;
    private DevicePushSender sender;

    @BeforeEach
    void setUp() {
        appender.start();
        logger.addAppender(appender);
        new DatabaseCleaner(jdbc).clean();
        jdbc.update(
                "INSERT INTO users(id,email,status,signature_original_storage_key) VALUES (?, 'notice@example.com', 'ACTIVE', 'notice/signature')",
                USER_ID);
        message = distributeNotice();
        addDevice();
        sender = mock(DevicePushSender.class);
        given(sender.send(any())).willReturn(DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("배포된 공지 알림을 공지 제목·본문으로 현재 기기에 보낸다")
    void process_distributedNotice_sendsNoticePush() {
        // When
        PushProcessingResult result = worker(NOW).process(message);

        // Then
        assertThat(result.retryable()).isFalse();
        ArgumentCaptor<DevicePushRequest> request = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender).send(request.capture());
        assertThat(request.getValue().title()).isEqualTo(TITLE);
        assertThat(request.getValue().body()).isEqualTo(BODY);
        assertThat(request.getValue().message().type()).isEqualTo(NotificationType.NOTICE);
        assertThat(request.getValue().message().sourceType())
                .isEqualTo(NotificationSourceType.NOTICE);
    }

    @Test
    @DisplayName("검수 결과 푸시를 끈 회원에게도 공지를 보낸다")
    void process_moderationPushDisabled_sendsNoticePush() {
        // Given
        jdbc.update("UPDATE users SET moderation_push_enabled=false");

        // When
        worker(NOW).process(message);

        // Then
        verify(sender).send(any());
    }

    @Test
    @DisplayName("정지 회원에게도 공지를 보낸다")
    void process_bannedUser_sendsNoticePush() {
        // Given
        jdbc.update("UPDATE users SET status='BANNED'");

        // When
        worker(NOW).process(message);

        // Then
        verify(sender).send(any());
    }

    @Test
    @DisplayName("이미 읽은 공지 알림은 보내지 않는다")
    void process_readNotice_skipsPush() {
        // Given
        jdbc.update("UPDATE notifications SET read_at=?", Timestamp.from(NOW));

        // When & Then
        assertSkipped(worker(NOW).process(message), "ALREADY_READ");
    }

    @Test
    @DisplayName("공지 배포 뒤 탈퇴한 회원에게는 보내지 않는다")
    void process_withdrawnAfterDistribution_skipsPush() {
        // Given
        jdbc.update("UPDATE users SET deleted_at=?", Timestamp.from(NOW));

        // When & Then
        assertSkipped(worker(NOW).process(message), "LOGIN_UNUSABLE");
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("배포 후 30분 직전까지만 보내고 정각·이후에는 보내지 않는다")
    void process_deadlineBoundary_stopsAtDeadline(long seconds) {
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(seconds)).process(message);

        // Then
        if (seconds < 1800) {
            verify(sender).send(any());
            return;
        }
        assertSkipped(result, "EXPIRED");
    }

    @Test
    @DisplayName("알림 ID가 없거나 대상 종류가 공지가 아닌 공지 메시지는 보내지 않는다")
    void process_malformedNoticeMessage_skipsPush() {
        // Given
        PushMessage withoutNotificationId = new PushMessage(
                message.eventId(),
                null,
                message.userId(),
                message.type(),
                message.sourceType(),
                message.sourceId(),
                message.occurredAt(),
                message.expiresAt()
        );
        PushMessage postSource = new PushMessage(
                message.eventId(),
                message.notificationId(),
                message.userId(),
                message.type(),
                NotificationSourceType.POST,
                message.sourceId(),
                message.occurredAt(),
                message.expiresAt()
        );

        // When
        worker(NOW).process(withoutNotificationId);
        worker(NOW).process(postSource);

        // Then
        verifyNoInteractions(sender);
        assertThat(logsAtStage("worker_skipped")).hasSize(2)
                .allSatisfy(log -> assertThat(log).containsEntry("reason", "INVALID_MESSAGE"));
    }

    @Test
    @DisplayName("다른 사건으로 조작한 공지 메시지는 보내지 않는다")
    void process_tamperedNoticeMessage_skipsPush() {
        // Given
        PushMessage tampered = new PushMessage(
                UUID.randomUUID(),
                message.notificationId(),
                message.userId(),
                message.type(),
                message.sourceType(),
                message.sourceId(),
                message.occurredAt(),
                message.expiresAt()
        );

        // When & Then
        assertSkipped(worker(NOW).process(tampered), "MESSAGE_MISMATCH");
    }

    @Test
    @DisplayName("배포 후 삭제된 공지의 알림은 보내지 않는다")
    void process_deletedNotice_skipsPush() {
        // Given
        jdbc.update("UPDATE notices SET deleted_at = ?", Timestamp.from(NOW));

        // When & Then
        assertSkipped(worker(NOW).process(message), "NOTIFICATION_UNAVAILABLE");
    }

    private void assertSkipped(PushProcessingResult result, String reason) {
        assertThat(result.retryable()).isFalse();
        verifyNoInteractions(sender);
        assertThat(logsAtStage("worker_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", reason));
    }

    // 실제 배포 경로로 공지 알림을 만들고, Relay가 발행하는 것과 같은 메시지를 구성한다.
    private PushMessage distributeNotice() {
        return new TransactionTemplate(manager).execute(status -> {
            Notice notice = notices.saveAndFlush(Notice.create(
                    TITLE,
                    BODY,
                    NOW,
                    NOW
            ));
            notifications.createNoticeNotifications(notice, NOW);
            UUID notificationId = jdbc.queryForObject(
                    "SELECT id FROM notifications WHERE user_id = ?",
                    UUID.class,
                    USER_ID
            );
            Notification notification = notifications
                    .findForPushByIdAndUserId(notificationId, USER_ID).orElseThrow();
            return new PushMessage(
                    notification.getEventKey(),
                    notification.getId(),
                    USER_ID,
                    notification.getType(),
                    notification.getSourceType(),
                    notification.getSourceId(),
                    notification.getCreatedAt(),
                    notification.getPushExpiresAt()
            );
        });
    }

    private void addDevice() {
        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO user_refresh_tokens(user_id,session_id,token_hash,expires_at,absolute_expires_at) VALUES (?,?,?,?,?)",
                USER_ID,
                sessionId,
                new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(7200))
        );
        new TransactionTemplate(manager).executeWithoutResult(status -> devices.save(
                new PushDevice(
                        users.findById(USER_ID).orElseThrow(),
                        sessionId,
                        new FcmToken("notice-token"),
                        NOW.plusSeconds(1)
                )));
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        return appender.list.stream().map(event -> {
            Map<String, Object> values = new HashMap<>();
            event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
            return values;
        }).filter(values -> stage.equals(values.get("stage"))).toList();
    }

    private PushWorkerService worker(Instant now) {
        return new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(now, ZoneOffset.UTC),
                manager
        );
    }
}
