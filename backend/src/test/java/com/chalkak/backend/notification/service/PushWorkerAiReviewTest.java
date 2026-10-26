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
import com.chalkak.backend.notification.domain.AiReviewOutcome;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.PushDevice;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushWorkerAiReviewTest extends IntegrationTestSupport {
    @Autowired
    private PushDeliveryService deliveryService;

    private static final Instant NOW = Instant.parse("2026-10-21T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final UUID AI_REVIEW_ID = UUID.randomUUID();

    @Autowired
    private NotificationRepository notifications;

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
                "INSERT INTO users(id,email,status,signature_original_storage_key) VALUES (?, 'ai-review@example.com', 'ACTIVE', 'ai-review/signature')",
                USER_ID);
        UUID topicId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?, '주제', CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')",
                topicId);
        jdbc.update(
                "INSERT INTO photos(id,original_storage_key) VALUES (?, 'ai-review/original')",
                photoId);
        jdbc.update(
                "INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status) VALUES (?,?,?,?, 'APPROVED')",
                POST_ID,
                USER_ID,
                topicId,
                photoId
        );
        message = createNotificationMessage();
        addDevice();
        sender = mock(DevicePushSender.class);
        given(sender.send(any(), any())).willReturn(DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("AI 리뷰 알림을 저장된 문구와 종류·게시물 대상으로 현재 기기에 보낸다")
    void process_aiReviewNotification_sendsStoredMessage() {
        // When
        PushProcessingResult result = worker(NOW).process(message);

        // Then
        assertThat(result.retryable()).isFalse();
        ArgumentCaptor<DevicePushRequest> request = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender).send(request.capture(), any());
        assertThat(request.getValue().title()).isEqualTo("AI 피드백이 도착했어요");
        assertThat(request.getValue().body()).isEqualTo("사진의 구도 점수와 피드백을 확인해 보세요.");
        assertThat(request.getValue().message().type())
                .isEqualTo(NotificationType.AI_REVIEW_COMPLETED);
        assertThat(request.getValue().message().sourceType())
                .isEqualTo(NotificationSourceType.POST);
        assertThat(request.getValue().message().sourceId()).isEqualTo(POST_ID);
        assertThat(request.getValue().message().notificationId())
                .isEqualTo(message.notificationId());
    }

    @Test
    @DisplayName("승인·반려와 주제 푸시를 꺼도 AI 리뷰 결과 설정이 켜져 있으면 보낸다")
    void process_otherPushSettingsDisabled_sendsAiReviewPush() {
        // Given
        jdbc.update("UPDATE users SET moderation_push_enabled=false, topic_push_enabled=false");

        // When
        worker(NOW).process(message);

        // Then
        verify(sender).send(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"read,ALREADY_READ", "postDeleted,NOTIFICATION_UNAVAILABLE",
            "disabled,PUSH_DISABLED"})
    @DisplayName("읽음·게시물 삭제·AI 리뷰 결과 설정 꺼짐이면 발송을 생략한다")
    void process_ineligibleState_skipsPush(String state, String reason) {
        // Given
        if (state.equals("read")) {
            jdbc.update("UPDATE notifications SET read_at=?", Timestamp.from(NOW));
        }
        if (state.equals("postDeleted")) {
            jdbc.update("UPDATE posts SET deleted_at=?", Timestamp.from(NOW));
        }
        if (state.equals("disabled")) {
            jdbc.update("UPDATE users SET ai_review_push_enabled=false");
        }

        // When & Then
        assertSkipped(worker(NOW).process(message), reason);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("리뷰 종료 후 30분 직전까지만 보내고 정각·이후에는 보내지 않는다")
    void process_deadlineBoundary_stopsAtDeadline(long seconds) {
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(seconds)).process(message);

        // Then
        if (seconds < 1800) {
            verify(sender).send(any(), any());
            return;
        }
        assertSkipped(result, "EXPIRED");
    }

    private void assertSkipped(PushProcessingResult result, String reason) {
        assertThat(result.retryable()).isFalse();
        verifyNoInteractions(sender);
        assertThat(logsAtStage("worker_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", reason));
    }

    // 결과 알림 저장 경로로 알림을 만들고, Relay가 발행하는 것과 같은 메시지를 구성한다.
    private PushMessage createNotificationMessage() {
        return new TransactionTemplate(manager).execute(status -> {
            notifications.insertIfAbsent(Notification.aiReview(
                    USER_ID,
                    POST_ID,
                    AI_REVIEW_ID,
                    1,
                    AiReviewOutcome.COMPLETED,
                    NOW,
                    true
            ));
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
                        new FcmToken("ai-review-token"),
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
                manager,
                deliveryService
        );
    }
}
