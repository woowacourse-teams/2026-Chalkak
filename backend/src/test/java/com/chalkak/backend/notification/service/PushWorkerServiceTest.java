package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

import com.chalkak.backend.auth.service.LoginSessionService;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PushWorkerServiceTest extends IntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    @Autowired
    private NotificationRepository notifications;
    @Autowired
    private PushDeviceRepository devices;
    @Autowired
    private UserRepository users;
    @Autowired
    private LoginSessionService sessions;
    @Autowired
    private PlatformTransactionManager manager;
    @Autowired
    private JdbcTemplate jdbc;
    private PushMessage message;
    private DevicePushSender sender;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbc).clean();
        jdbc.update(
                "INSERT INTO users(id,email,status,signature_original_storage_key) VALUES (?, 'worker@example.com', 'ACTIVE', 'worker/signature')",
                USER_ID);
        UUID topicId = UUID.randomUUID();
        UUID photoId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO topics(id,title,topic_date,starts_at,ends_at) VALUES (?, '주제', CURRENT_DATE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour')",
                topicId);
        jdbc.update("INSERT INTO photos(id,original_storage_key) VALUES (?, 'worker/original')",
                photoId);
        jdbc.update(
                "INSERT INTO posts(id,user_id,topic_id,photo_id,moderation_status) VALUES (?,?,?,?, 'APPROVED')",
                POST_ID, USER_ID, topicId, photoId);
        message = new TransactionTemplate(manager).execute(status -> {
            Notification notification = notifications
                    .save(Notification.approved(USER_ID, POST_ID, UUID.randomUUID(), NOW, true));
            return new PushMessage(notification.getEventKey(), notification.getId(), USER_ID,
                    notification.getType(), notification.getSourceType(), POST_ID, NOW,
                    NOW.plusSeconds(1800));
        });
        deviceId = addDevice("worker-token");
        sender = mock(DevicePushSender.class);
        given(sender.send(any())).willReturn(
                new DevicePushResult(DevicePushResult.Status.ACCEPTED, null, Duration.ZERO));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TRIGGER IF EXISTS worker_reject_device_delete ON push_devices");
        jdbc.execute("DROP FUNCTION IF EXISTS worker_reject_device_delete()");
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("현재 등록 기기로 보내며 최초 SQS 상태 반영이 실패한 대기 알림도 처리한다")
    void process_pendingNotification_sendsToCurrentDevice() {
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result.retryable()).isFalse();
        verify(sender).send(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("발생 후 30분 직전만 발송하고 정각·이후에는 완료한다")
    void process_deadlineBoundary_stopsAtDeadline(long seconds) {
        // When
        assertThat(worker(NOW.plusSeconds(seconds)).process(message, sender).retryable()).isFalse();
        // Then
        if (seconds < 1800) {
            verify(sender).send(any());
            return;
        }
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"read", "postDeleted", "withdrawn", "disabled", "revoked", "expired",
            "missingDevice", "missingNotification"})
    @DisplayName("발송 직전 읽음·삭제·탈퇴·설정·로그인·기기 상태를 확인하여 발송을 생략한다")
    void process_ineligibleLatestState_completesWithoutSending(String state) {
        // Given
        if (state.equals("read"))
            jdbc.update("UPDATE notifications SET read_at=?", Timestamp.from(NOW));
        if (state.equals("postDeleted"))
            jdbc.update("UPDATE posts SET deleted_at=?", Timestamp.from(NOW));
        if (state.equals("withdrawn"))
            jdbc.update("UPDATE users SET deleted_at=?", Timestamp.from(NOW));
        if (state.equals("disabled"))
            jdbc.update("UPDATE users SET moderation_push_enabled=false");
        if (state.equals("revoked"))
            jdbc.update("UPDATE user_refresh_tokens SET revoked_at=?", Timestamp.from(NOW));
        if (state.equals("expired"))
            jdbc.update("UPDATE user_refresh_tokens SET expires_at=?", Timestamp.from(NOW));
        if (state.equals("missingDevice"))
            jdbc.update("DELETE FROM push_devices");
        if (state.equals("missingNotification"))
            jdbc.update("DELETE FROM notifications");
        // When & Then
        assertThat(worker(NOW).process(message, sender).retryable()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("정지 회원에게도 검수 결과 푸시를 보낸다")
    void process_bannedUser_sendsModerationPush() {
        // Given
        jdbc.update("UPDATE users SET status='BANNED'");
        // When
        worker(NOW).process(message, sender);
        // Then
        verify(sender).send(any());
    }

    @Test
    @DisplayName("모든 기기가 일시 실패하면 재전달을 요청하며 가장 긴 Retry-After를 유지한다")
    void process_allTransientFailures_retriesWithLongestDelay() {
        // Given
        addDevice("second-token");
        given(sender.send(any())).willReturn(
                new DevicePushResult(DevicePushResult.Status.RETRYABLE, "UNAVAILABLE",
                        Duration.ofSeconds(60)),
                new DevicePushResult(DevicePushResult.Status.RETRYABLE, "QUOTA",
                        Duration.ofSeconds(120)));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    @DisplayName("기기 하나라도 수락되면 나머지 일시 실패를 재시도하지 않는다")
    void process_partialAcceptance_completesUserMessage() {
        // Given
        addDevice("second-token");
        given(sender.send(any())).willReturn(
                new DevicePushResult(DevicePushResult.Status.RETRYABLE, "UNAVAILABLE",
                        Duration.ofSeconds(60)),
                new DevicePushResult(DevicePushResult.Status.ACCEPTED, null, Duration.ZERO));
        // When & Then
        assertThat(worker(NOW).process(message, sender).retryable()).isFalse();
        verify(sender, times(2)).send(any());
    }

    @Test
    @DisplayName("첫 기기 수락 후 다음 기기의 예외가 발생해도 사용자 작업을 완료한다")
    void process_acceptedThenDeviceException_completesUserMessage() {
        // Given
        addDevice("second-token");
        given(sender.send(any()))
                .willReturn(new DevicePushResult(DevicePushResult.Status.ACCEPTED, null,
                        Duration.ZERO))
                .willThrow(new IllegalStateException("device processing failure"));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verify(sender, times(2)).send(any());
    }

    @Test
    @DisplayName("첫 기기의 예외가 발생해도 다음 기기를 발송하고 수락되면 완료한다")
    void process_deviceExceptionThenAccepted_completesUserMessage() {
        // Given
        addDevice("second-token");
        given(sender.send(any()))
                .willThrow(new IllegalStateException("device processing failure"))
                .willReturn(new DevicePushResult(DevicePushResult.Status.ACCEPTED, null,
                        Duration.ZERO));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verify(sender, times(2)).send(any());
    }

    @Test
    @DisplayName("수락한 기기 없이 예상하지 못한 예외가 발생하면 재전달을 요청한다")
    void process_deviceExceptionWithoutAcceptance_retriesUserMessage() {
        // Given
        given(sender.send(any())).willThrow(new IllegalStateException("device processing failure"));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
    }

    @Test
    @DisplayName("첫 기기 수락 후 다음 기기의 무효 토큰 삭제가 실패해도 완료한다")
    void process_acceptedThenInvalidTokenCleanupFailure_completesUserMessage() {
        // Given
        UUID other = addDevice("second-token");
        rejectDeviceDeletion();
        given(sender.send(any())).willReturn(
                new DevicePushResult(DevicePushResult.Status.ACCEPTED, null, Duration.ZERO),
                new DevicePushResult(DevicePushResult.Status.INVALID_TOKEN, "UNREGISTERED",
                        Duration.ZERO));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactlyInAnyOrder(deviceId, other);
        verify(sender, times(2)).send(any());
    }

    @Test
    @DisplayName("무효 토큰 삭제 실패는 재발송 사유가 아니며 기기 행은 롤백되어 남는다")
    void process_invalidTokenCleanupFailure_preservesDeviceAndCompletes() {
        // Given
        rejectDeviceDeletion();
        given(sender.send(any())).willReturn(new DevicePushResult(
                DevicePushResult.Status.INVALID_TOKEN, "UNREGISTERED", Duration.ZERO));
        // When
        PushProcessingResult result = worker(NOW).process(message, sender);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
    }

    @Test
    @DisplayName("등록 해제 토큰의 해당 기기만 제거한다")
    void process_unregisteredToken_deletesAffectedDevice() {
        // Given
        UUID other = addDevice("other-token");
        given(sender.send(any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("worker-token"))
                return new DevicePushResult(DevicePushResult.Status.INVALID_TOKEN, "UNREGISTERED",
                        Duration.ZERO);
            return new DevicePushResult(DevicePushResult.Status.ACCEPTED, null, Duration.ZERO);
        });
        // When
        worker(NOW).process(message, sender);
        // Then
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(other);
    }

    @Test
    @DisplayName("실패 응답 전에 갱신된 토큰은 이전 무효 토큰 제거로 삭제하지 않는다")
    void process_tokenUpdatedDuringSend_keepsNewToken() {
        // Given
        given(sender.send(any())).willAnswer(call -> {
            jdbc.update(
                    "UPDATE push_devices SET fcm_token='updated-token',fcm_token_hash=? WHERE id=?",
                    new FcmToken("updated-token").getHash(), deviceId);
            return new DevicePushResult(DevicePushResult.Status.INVALID_TOKEN, "UNREGISTERED",
                    Duration.ZERO);
        });
        // When
        worker(NOW).process(message, sender);
        // Then
        assertThat(jdbc.queryForObject("SELECT fcm_token FROM push_devices WHERE id=?",
                String.class, deviceId)).isEqualTo("updated-token");
    }

    @Test
    @DisplayName("내용·프로젝트 영구 오류는 기기를 지우지 않고 완료한다")
    void process_permanentFailure_preservesDeviceAndCompletes() {
        // Given
        given(sender.send(any())).willReturn(new DevicePushResult(
                DevicePushResult.Status.PERMANENT_FAILURE, "INVALID_ARGUMENT", Duration.ZERO));
        // When & Then
        assertThat(worker(NOW).process(message, sender).retryable()).isFalse();
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
    }

    @Test
    @DisplayName("다른 사건이나 임의로 연장한 기한의 메시지는 발송하지 않는다")
    void process_tamperedMessage_doesNotSend() {
        // Given
        PushMessage invalid = new PushMessage(UUID.randomUUID(), message.notificationId(), USER_ID,
                message.type(), message.sourceType(), POST_ID, NOW, NOW.plusSeconds(3600));
        // When & Then
        assertThat(worker(NOW).process(invalid, sender).retryable()).isFalse();
        verifyNoInteractions(sender);
    }

    private PushWorkerService worker(Instant now) {
        return new PushWorkerService(notifications, devices, sessions,
                Clock.fixed(now, ZoneOffset.UTC), manager);
    }

    private void rejectDeviceDeletion() {
        jdbc.execute("""
                CREATE FUNCTION worker_reject_device_delete() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'device cleanup failure';
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER worker_reject_device_delete BEFORE DELETE ON push_devices
                FOR EACH ROW EXECUTE FUNCTION worker_reject_device_delete()
                """);
    }

    private UUID addDevice(String token) {
        UUID sessionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO user_refresh_tokens(user_id,session_id,token_hash,expires_at,absolute_expires_at) VALUES (?,?,?,?,?)",
                USER_ID, sessionId, new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(NOW.plusSeconds(3600)), Timestamp.from(NOW.plusSeconds(7200)));
        return new TransactionTemplate(manager).execute(status -> {
            PushDevice device = new PushDevice(users.findById(USER_ID).orElseThrow(), sessionId,
                    new FcmToken(token), NOW.plusSeconds(1));
            devices.save(device);
            return device.getId();
        });
    }
}
