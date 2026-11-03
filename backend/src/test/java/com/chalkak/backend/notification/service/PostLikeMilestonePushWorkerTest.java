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
import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PostLikePushMilestoneRepository;
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
    private PostLikePushMilestoneRepository milestones;
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
        PostLikePushMilestone milestone = savePublishedMilestone(10L);
        message = new PushMessage(
                milestone.getId(),
                null,
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
            assertThat(request.body()).isEqualTo("좋아요 10개를 받았어요");
            assertThat(request.message().eventId()).isEqualTo(message.eventId());
            assertThat(request.message().notificationId()).isNull();
            assertThat(request.message().sourceId()).isEqualTo(POST_ID);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUSH_DISABLED", "POST_DELETED", "WITHDRAWN", "NO_DEVICE"})
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
                null,
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
                milestones,
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

    private PostLikePushMilestone savePublishedMilestone(long count) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            PostLikePushMilestone milestone = milestones.save(PostLikePushMilestone.create(
                    POST_ID,
                    USER_ID,
                    count,
                    NOW
            ));
            milestone.markPublished(NOW);
            return milestone;
        });
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
