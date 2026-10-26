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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class PushWorkerTopicTest extends IntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-12T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TOPIC_ID = UUID.randomUUID();

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

    private DevicePushSender sender;
    private PushMessage message;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update(
                "INSERT INTO users(id,email,status,signature_original_storage_key) VALUES (?, 'topic-worker@example.com', 'ACTIVE', 'topic/signature')",
                USER_ID);
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?, '빛과 그림자', ?, ?, ?)",
                TOPIC_ID,
                Date.valueOf(LocalDate.ofInstant(NOW, ZoneId.of("Asia/Seoul"))),
                Timestamp.from(NOW.minusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(3600))
        );
        addDevice("topic-token");
        message = topicMessage(NOW);
        sender = mock(DevicePushSender.class);
        given(sender.send(any(), any())).willReturn(DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("오늘 주제에 제출하지 않은 회원에게 검수 설정과 관계없이 주제를 보내고 알림함에 저장하지 않는다")
    void process_noTopicSubmission_sendsWithoutInbox() {
        // Given
        jdbc.update("UPDATE users SET moderation_push_enabled=false WHERE id=?", USER_ID);
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        ArgumentCaptor<DevicePushRequest> request = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender).send(request.capture(), any());
        assertThat(request.getValue().message()).isEqualTo(message);
        assertThat(request.getValue().title()).isEqualTo("오늘의 주제");
        assertThat(request.getValue().body()).isEqualTo("빛과 그림자");
        assertThat(request.getValue().token()).isEqualTo("topic-token");
        assertThat(request.getValue().expiresAt()).isEqualTo(NOW.plusSeconds(1800));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class))
                .isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "VALIDATING, false, false", "PENDING, false, false", "APPROVED, false, false",
            "REJECTED, false, true", "VALIDATING, true, true", "PENDING, true, true",
            "APPROVED, true, true", "REJECTED, true, true"
    })
    @DisplayName("큐 등록 후 오늘 주제에 참여하면 발송을 생략하고 반려·삭제만 있으면 발송한다")
    void process_postCreatedAfterEnqueue_rechecksParticipation(
            ModerationStatus status,
            boolean deleted,
            boolean sendable
    ) {
        // Given
        insertPost(
                USER_ID,
                TOPIC_ID,
                status,
                deleted
        );

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        if (sendable) {
            verify(sender).send(any(), any());
            return;
        }
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("반려 후 재제출한 승인 대기 게시물이 있으면 재시도 없이 완료한다")
    void process_resubmittedPost_completesWithoutSending() {
        // Given
        insertPost(
                USER_ID,
                TOPIC_ID,
                ModerationStatus.REJECTED,
                false
        );
        insertPost(
                USER_ID,
                TOPIC_ID,
                ModerationStatus.PENDING,
                false
        );

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"otherTopic", "otherUser"})
    @DisplayName("다른 주제나 다른 회원의 제출은 현재 회원의 주제 발송을 막지 않는다")
    void process_unrelatedPost_sendsToCurrentUser(String state) {
        // Given
        UUID authorId = USER_ID;
        UUID topicId = TOPIC_ID;
        if (state.equals("otherTopic")) {
            topicId = UUID.randomUUID();
            jdbc.update(
                    """
                            INSERT INTO topics(id,title,topic_date,starts_at,ends_at)
                            VALUES (?,'다른 주제',?,?,?)
                            """,
                    topicId,
                    Date.valueOf("2026-10-11"),
                    Timestamp.from(NOW.minusSeconds(86400)),
                    Timestamp.from(NOW.minusSeconds(3600))
            );
        }
        if (state.equals("otherUser")) {
            authorId = UUID.randomUUID();
            jdbc.update(
                    """
                            INSERT INTO users(id,email,status,signature_original_storage_key)
                            VALUES (?,?,'ACTIVE',?)
                            """,
                    authorId,
                    authorId + "@topic-worker.test",
                    "signature/" + authorId
            );
        }
        insertPost(
                authorId,
                topicId,
                ModerationStatus.APPROVED,
                false
        );

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verify(sender).send(any(), any());
    }

    @Test
    @DisplayName("일시 실패 후 제출한 회원은 같은 메시지를 다시 받아도 FCM 재발송 없이 완료한다")
    void process_submissionBeforeRedelivery_completesWithoutResending() {
        // Given
        given(sender.send(any(), any())).willReturn(DevicePushResult.retryable("UNAVAILABLE"));
        PushWorkerService service = worker(NOW.plusSeconds(60));
        assertThat(service.process(message).retryable()).isTrue();
        insertPost(
                USER_ID,
                TOPIC_ID,
                ModerationStatus.PENDING,
                false
        );

        // When
        PushProcessingResult result = service.process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verify(sender, times(1)).send(any(), any());
    }

    @Test
    @DisplayName("첫 기기 처리 중 제출하면 다음 기기 요청 직전에 재확인해 추가 발송을 생략한다")
    void process_submissionBetweenDevices_rechecksBeforeEachSend() {
        // Given
        addDevice("second-topic-token");
        given(sender.send(any(), any())).willAnswer(call -> {
            insertPost(
                    USER_ID,
                    TOPIC_ID,
                    ModerationStatus.PENDING,
                    false
            );
            return DevicePushResult.accepted();
        });

        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verify(sender, times(1)).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"banned", "withdrawn", "disabled", "revoked", "expired",
            "missingDevice", "missingTopic", "deletedTopic", "differentTopic"})
    @DisplayName("주제 푸시의 최신 회원·수신 설정·오늘 주제·로그인·기기 조건이 바뀌면 생략한다")
    void process_ineligibleLatestState_completesWithoutSending(String state) {
        // Given
        if (state.equals("banned"))
            jdbc.update("UPDATE users SET status='BANNED'");
        if (state.equals("withdrawn"))
            jdbc.update("UPDATE users SET deleted_at=?", Timestamp.from(NOW));
        if (state.equals("disabled"))
            jdbc.update("UPDATE users SET topic_push_enabled=false");
        if (state.equals("revoked"))
            jdbc.update("UPDATE user_refresh_tokens SET revoked_at=?", Timestamp.from(NOW));
        if (state.equals("expired"))
            jdbc.update("UPDATE user_refresh_tokens SET expires_at=?", Timestamp.from(NOW));
        if (state.equals("missingDevice"))
            jdbc.update("DELETE FROM push_devices");
        if (state.equals("missingTopic"))
            jdbc.update("DELETE FROM topics");
        if (state.equals("deletedTopic"))
            jdbc.update("UPDATE topics SET deleted_at=?", Timestamp.from(NOW));
        if (state.equals("differentTopic")) {
            message = new PushMessage(
                    message.eventId(),
                    null,
                    USER_ID,
                    NotificationType.TOPIC,
                    NotificationSourceType.TOPIC,
                    UUID.randomUUID(),
                    NOW,
                    message.expiresAt()
            );
        }
        // When & Then
        assertThat(worker(NOW.plusSeconds(60)).process(message))
                .isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("주제도 예정 시각부터 30분 직전만 발송하고 경계부터는 완료한다")
    void process_deadlineBoundary_stopsAtOriginalDeadline(long seconds) {
        // When & Then
        assertThat(worker(NOW.plusSeconds(seconds)).process(message))
                .isEqualTo(PushProcessingResult.completed());
        if (seconds < 1800) {
            verify(sender).send(any(), any());
            return;
        }
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("KST 날짜가 바뀌면 기한이 남아도 전날 주제 메시지를 보내지 않는다")
    void process_previousDayTopicAfterMidnight_completesWithoutSending() {
        // Given
        Instant scheduledAt = Instant.parse("2026-10-11T14:55:00Z");
        jdbc.update("UPDATE topics SET topic_date=?", Date.valueOf("2026-10-11"));
        message = topicMessage(scheduledAt);
        // When & Then
        assertThat(worker(Instant.parse("2026-10-11T15:00:00Z")).process(message))
                .isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"notificationId", "sourceType", "deadline", "future", "eventId"})
    @DisplayName("알림함 ID·게시물 대상·연장 기한·미래 시각·누락 식별자가 있는 주제 메시지는 보내지 않는다")
    void process_invalidTopicMessage_completesWithoutSending(String invalidField) {
        // Given
        UUID notificationId = null;
        UUID eventId = message.eventId();
        NotificationSourceType sourceType = NotificationSourceType.TOPIC;
        Instant occurredAt = NOW;
        Instant expiresAt = message.expiresAt();
        if (invalidField.equals("notificationId"))
            notificationId = UUID.randomUUID();
        if (invalidField.equals("sourceType"))
            sourceType = NotificationSourceType.POST;
        if (invalidField.equals("deadline"))
            expiresAt = NOW.plusSeconds(3600);
        if (invalidField.equals("future")) {
            occurredAt = NOW.plusSeconds(60);
            expiresAt = occurredAt.plusSeconds(1800);
        }
        if (invalidField.equals("eventId"))
            eventId = null;
        PushMessage invalid = new PushMessage(
                eventId,
                notificationId,
                USER_ID,
                NotificationType.TOPIC,
                sourceType,
                TOPIC_ID,
                occurredAt,
                expiresAt
        );
        // When & Then
        assertThat(worker(NOW).process(invalid)).isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("재전달 때는 당시의 기기를 다시 조회하고 새 등록 기기로 발송한다")
    void process_redelivery_usesCurrentDevices() {
        // Given
        given(sender.send(any(), any())).willReturn(DevicePushResult.retryable("UNAVAILABLE"),
                DevicePushResult.accepted());
        assertThat(worker(NOW.plusSeconds(60)).process(message).retryable()).isTrue();
        jdbc.update("DELETE FROM push_devices");
        addDevice("new-topic-token");
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(120)).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        ArgumentCaptor<DevicePushRequest> requests = ArgumentCaptor
                .forClass(DevicePushRequest.class);
        verify(sender, times(2)).send(requests.capture(), any());
        assertThat(requests.getAllValues()).extracting(DevicePushRequest::token)
                .containsExactly("topic-token", "new-topic-token");
    }

    @Test
    @DisplayName("주제도 기기 하나가 수락하면 다른 기기의 실패를 재시도하지 않는다")
    void process_partialAcceptance_completesUserMessage() {
        // Given
        addDevice("second-topic-token");
        given(sender.send(any(), any())).willReturn(DevicePushResult.accepted(),
                DevicePushResult.retryable("UNAVAILABLE"));
        // When & Then
        assertThat(worker(NOW.plusSeconds(60)).process(message))
                .isEqualTo(PushProcessingResult.completed());
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("주제의 모든 기기가 일시 실패하면 가장 긴 지연으로 SQS 재전달을 요청한다")
    void process_allTransientFailures_retriesWithLongestDelay() {
        // Given
        addDevice("second-topic-token");
        given(sender.send(any(), any())).willReturn(
                DevicePushResult.retryable("UNAVAILABLE", Duration.ofSeconds(60)),
                DevicePushResult.retryable("QUOTA_EXCEEDED", Duration.ofSeconds(120)));
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("주제의 영구 실패는 완료하고 무효 토큰일 때만 기기 행을 지운다")
    void process_permanentFailureAndInvalidToken_completesAndCleansOnlyInvalidDevice() {
        // Given
        addDevice("second-topic-token");
        given(sender.send(any(), any())).willReturn(
                DevicePushResult.permanentFailure("INVALID_ARGUMENT"),
                DevicePushResult.invalidToken("UNREGISTERED"));
        // When
        PushProcessingResult result = worker(NOW.plusSeconds(60)).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM push_devices", Integer.class))
                .isEqualTo(1);
    }

    private PushMessage topicMessage(Instant scheduledAt) {
        return new PushMessage(
                UUID.randomUUID(),
                null,
                USER_ID,
                NotificationType.TOPIC,
                NotificationSourceType.TOPIC,
                TOPIC_ID,
                scheduledAt,
                scheduledAt.plusSeconds(1800)
        );
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
                Timestamp.from(NOW.plusSeconds(1)),
                Timestamp.from(NOW.plusSeconds(1))
        );
    }

    private void insertPost(
            UUID userId,
            UUID topicId,
            ModerationStatus status,
            boolean deleted
    ) {
        UUID photoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO photos(id,original_storage_key) VALUES (?,?)",
                photoId,
                "topic/photo/" + photoId
        );
        Timestamp deletedAt = null;
        if (deleted) {
            deletedAt = Timestamp.from(NOW.plusSeconds(30));
        }
        jdbc.update(
                """
                        INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status,deleted_at,created_at)
                        VALUES (?,?,?,?,CAST(? AS moderation_status),?,?)
                        """,
                UUID.randomUUID(),
                userId,
                topicId,
                photoId,
                status.name(),
                deletedAt,
                Timestamp.from(NOW.plusSeconds(10))
        );
    }
}
