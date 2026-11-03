package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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

class PostLikeMilestonePushWorkerTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-11-03T00:00:00Z");
    private static final UUID USER_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432d101");
    private static final UUID POST_ID = UUID.fromString("019a47c0-3db8-72dc-8f2d-07a15432d104");

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
    private PushDeliveryRepository deliveries;
    @Autowired
    private PushDeliveryService deliveryService;
    @Autowired
    private UserRepository users;
    @Autowired
    private LoginSessionService sessions;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DevicePushSender sender;
    private PushMessage message;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        insertPost();
        Notification notification = saveNotification(10L);
        message = new PushMessage(
                notification.getEventKey(),
                notification.getId(),
                USER_ID,
                NotificationType.POST_LIKE_MILESTONE,
                NotificationSourceType.POST,
                POST_ID,
                NOW,
                NOW.plusSeconds(1800)
        );
        addDevice("like-milestone-token");
        sender = mock(DevicePushSender.class);
        given(sender.send(any(), any())).willReturn(DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("좋아요 마일스톤을 모든 현재 기기에 업적 문구와 게시물 대상으로 발송한다")
    void process_validMilestone_sendsAchievementPushToAllDevices() {
        // Given
        addDevice("second-like-milestone-token");

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(1)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        ArgumentCaptor<DevicePushRequest> requests = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender, times(2)).send(requests.capture(), any());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.title()).isEqualTo("게시물에서 새로운 업적을 달성했어요");
            assertThat(request.body()).isEqualTo("좋아요 10개 이상을 달성했어요");
            assertThat(request.message().eventId()).isEqualTo(message.eventId());
            assertThat(request.message().notificationId()).isEqualTo(message.notificationId());
            assertThat(request.message().sourceId()).isEqualTo(POST_ID);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUSH_DISABLED", "POST_DELETED", "WITHDRAWN", "NO_DEVICE", "READ",
            "REJECTED", "EXPIRED", "NOT_REQUIRED", "FAILED", "MISSING"})
    @DisplayName("최신 발송 조건을 충족하지 않으면 FCM을 호출하지 않고 완료한다")
    void process_latestConditionUnavailable_completesWithoutSending(String condition) {
        // Given
        switch (condition) {
            case "PUSH_DISABLED" -> jdbcTemplate.update(
                    "UPDATE users SET like_push_enabled = false WHERE id = ?", USER_ID);
            case "POST_DELETED" -> jdbcTemplate.update(
                    "UPDATE posts SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    POST_ID
            );
            case "WITHDRAWN" -> jdbcTemplate.update(
                    "UPDATE users SET deleted_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    USER_ID
            );
            case "READ" -> jdbcTemplate.update(
                    "UPDATE notifications SET read_at = ? WHERE id = ?",
                    Timestamp.from(NOW),
                    message.notificationId()
            );
            case "REJECTED" -> jdbcTemplate.update(
                    "UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?", POST_ID
            );
            case "EXPIRED", "NOT_REQUIRED", "FAILED" -> jdbcTemplate.update(
                    "UPDATE notifications SET sqs_publish_status = ?, next_attempt_at = NULL, sqs_published_at = NULL WHERE id = ?",
                    condition,
                    message.notificationId()
            );
            case "MISSING" -> jdbcTemplate.update(
                    "DELETE FROM notifications WHERE id = ?", message.notificationId()
            );
            default -> jdbcTemplate.update("DELETE FROM push_devices WHERE user_id = ?", USER_ID);
        }

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(1)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("이용 정지 회원도 사용 가능한 로그인과 기기가 있으면 발송한다")
    void process_bannedUser_sendsToUsableDevice() {
        // Given
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", USER_ID);

        // When
        worker(NOW.plusSeconds(1)).process(message);

        // Then
        verify(sender).send(any(), any());
    }

    @Test
    @DisplayName("좋아요 수가 마일스톤 아래로 감소해도 달성 기록을 기준으로 발송한다")
    void process_likeCountDecreased_stillSendsRecordedMilestone() {
        // Given
        jdbcTemplate.update(
                "INSERT INTO post_like_counts (post_id, like_count) VALUES (?, 4)", POST_ID);

        // When
        worker(NOW.plusSeconds(1)).process(message);

        // Then
        verify(sender).send(any(), any());
    }

    @Test
    @DisplayName("같은 마일스톤 메시지가 다시 전달돼도 완료 이력으로 재발송하지 않는다")
    void process_completedMilestoneRedelivered_doesNotResend() {
        // Given
        worker(NOW.plusSeconds(1)).process(message);

        // When
        PushProcessingResult repeated = worker(NOW.plusSeconds(2)).process(message);

        // Then
        assertThat(repeated).isEqualTo(PushProcessingResult.completed());
        verify(sender).send(any(), any());
        PushDelivery delivery = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(delivery.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("DB 마일스톤과 사건 정보가 다르면 발송 이력을 만들지 않고 생략한다")
    void process_mismatchedMessage_doesNotSendOrCreateDelivery() {
        // Given
        PushMessage mismatched = new PushMessage(
                message.eventId(),
                message.notificationId(),
                USER_ID,
                NotificationType.POST_LIKE_MILESTONE,
                NotificationSourceType.POST,
                POST_ID,
                NOW.plusSeconds(1),
                NOW.plusSeconds(1801)
        );

        // When
        worker(NOW.plusSeconds(1)).process(mismatched);

        // Then
        verifyNoInteractions(sender);
        assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)).isEmpty();
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
                transactionManager,
                deliveryService
        );
    }

    @Test
    @DisplayName("좋아요 알림도 수락한 기기는 생략하고 일시 실패한 기기만 다시 보낸다")
    void process_partialAcceptance_retriesOnlyFailedDevice() {
        // Given
        addDevice("retry-like-token");
        AtomicInteger retryDeviceAttempts = new AtomicInteger();
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("retry-like-token")
                    && retryDeviceAttempts.incrementAndGet() == 1) {
                return DevicePushResult.retryable("UNAVAILABLE");
            }
            return DevicePushResult.accepted();
        });

        // When
        PushProcessingResult first = worker(NOW.plusSeconds(1)).process(message);
        PushProcessingResult second = worker(NOW.plusSeconds(61)).process(message);

        // Then
        assertThat(first.retryable()).isTrue();
        assertThat(second.retryable()).isFalse();
        ArgumentCaptor<DevicePushRequest> requests = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender, times(3)).send(requests.capture(), any());
        assertThat(requests.getAllValues())
                .filteredOn(request -> request.token().equals("like-milestone-token"))
                .hasSize(1);
        assertThat(requests.getAllValues())
                .filteredOn(request -> request.token().equals("retry-like-token"))
                .hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799L, 1800L, 1801L})
    @DisplayName("좋아요 알림은 생성 후 30분 정각부터 FCM 요청을 시작하지 않는다")
    void process_deadlineBoundary_skipsAtThirtyMinutes(long seconds) {
        // When
        worker(NOW.plusSeconds(seconds)).process(message);

        // Then
        if (seconds < 1800) {
            verify(sender).send(any(), any());
            return;
        }
        verifyNoInteractions(sender);
    }

    private Notification saveNotification(long count) {
        return new TransactionTemplate(transactionManager)
                .execute(status -> notifications.save(Notification.likeMilestone(
                        USER_ID,
                        POST_ID,
                        count,
                        NOW,
                        true
                ))
                );
    }

    @Test
    @DisplayName("기존 Relay가 좋아요 Notification을 알림 ID와 함께 발행하고 Worker가 전송한다")
    void process_notificationRelay_sendsUsingSharedPipeline() {
        // Given
        PushMessagePublisher publisher = mock(PushMessagePublisher.class);
        given(publisher.publish(any())).willReturn(PushPublicationResult.accepted("sqs-like"));
        NotificationRelayService relay = new NotificationRelayService(
                notifications,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager
        );

        // When
        relay.publishPendingNotifications();

        // Then
        ArgumentCaptor<PushMessage> published = ArgumentCaptor.forClass(PushMessage.class);
        verify(publisher).publish(published.capture());
        assertThat(published.getValue()).isEqualTo(message);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sqs_publish_status FROM notifications WHERE id = ?",
                String.class,
                message.notificationId()
        )).isEqualTo("PUBLISHED");
        worker(NOW.plusSeconds(1)).process(published.getValue());
        verify(sender).send(any(), any());
    }

    private UUID addDevice(String token) {
        UUID sessionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO user_refresh_tokens(user_id,session_id,token_hash,expires_at,absolute_expires_at) VALUES (?,?,?,?,?)",
                USER_ID,
                sessionId,
                new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(7200))
        );
        return new TransactionTemplate(transactionManager).execute(status -> {
            PushDevice device = new PushDevice(
                    users.findById(USER_ID).orElseThrow(),
                    sessionId,
                    new FcmToken(token),
                    NOW.plusSeconds(1)
            );
            devices.save(device);
            return device.getId();
        });
    }

    private void insertPost() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (?, 'milestone-worker@example.com', 'ACTIVE', 'signatures/worker',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES ('019a47c0-3db8-72dc-8f2d-07a15432d102', 'Worker 주제', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, created_at, updated_at)
                VALUES ('019a47c0-3db8-72dc-8f2d-07a15432d103', 'posts/worker',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                        ) VALUES (?, ?, '019a47c0-3db8-72dc-8f2d-07a15432d102',
                            '019a47c0-3db8-72dc-8f2d-07a15432d103', 'APPROVED',
                            CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                POST_ID,
                USER_ID
        );
    }
}
