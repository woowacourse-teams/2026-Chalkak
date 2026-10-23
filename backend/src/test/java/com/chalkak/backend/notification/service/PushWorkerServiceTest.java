package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.params.provider.CsvSource;
import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import com.chalkak.backend.notification.repository.ReminderRepository;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.repository.UserRepository;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    private final Logger logger = (Logger) LoggerFactory
            .getLogger("com.chalkak.backend.notification");
    private String failingLogStage;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        public synchronized void doAppend(ILoggingEvent event) {
            if (event.getKeyValuePairs() != null && event.getKeyValuePairs().stream()
                    .anyMatch(pair -> pair.key.equals("stage")
                            && pair.value.equals(failingLogStage))) {
                throw new IllegalStateException("timing log failed");
            }
            super.doAppend(event);
        }

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };
    private PushMessage message;
    private DevicePushSender sender;
    private UUID deviceId;

    @BeforeEach
    void setUp() {
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
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
                POST_ID,
                USER_ID,
                topicId,
                photoId
        );
        message = new TransactionTemplate(manager).execute(status -> {
            Notification notification = notifications
                    .save(Notification.approved(
                            USER_ID,
                            POST_ID,
                            UUID.randomUUID(),
                            NOW,
                            true
                    ));
            return new PushMessage(
                    notification.getEventKey(),
                    notification.getId(),
                    USER_ID,
                    notification.getType(),
                    notification.getSourceType(),
                    POST_ID,
                    NOW,
                    NOW.plusSeconds(1800)
            );
        });
        deviceId = addDevice("worker-token");
        sender = mock(DevicePushSender.class);
        given(sender.send(any())).willReturn(
                DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS worker_reject_device_delete ON push_devices");
        jdbc.execute("DROP FUNCTION IF EXISTS worker_reject_device_delete()");
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("현재 등록 기기로 보내며 최초 SQS 상태 반영이 실패한 대기 알림도 처리한다")
    void process_pendingNotification_sendsToCurrentDevice() {
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isFalse();
        verify(sender).send(any());
        assertThat(logsAtStage("worker_db_transaction"))
                .extracting(log -> log.get("operation"))
                .containsExactly("DEVICE_LOOKUP", "LATEST_CONDITIONS");
        assertThat(logsAtStage("worker_db_transaction")).allSatisfy(log -> {
            assertThat(log).containsEntry("transactionCompleted", true)
                    .containsEntry("eventId", message.eventId());
            assertThat((long) log.get("dbTransactionDurationMs")).isNotNegative();
        });
    }

    @Test
    @DisplayName("DB 계측 로그가 실패해도 기기 조회와 발송을 완료한다")
    void process_timingLogFailure_completesDelivery() {
        // Given
        failingLogStage = "worker_db_transaction";
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isFalse();
        verify(sender).send(any());
    }

    @Test
    @DisplayName("기기가 없으면 목록 조회 시간과 0개 집계를 남기고 발송하지 않는다")
    void process_noDevices_recordsLookupWithoutSending() {
        // Given
        jdbc.update("DELETE FROM push_devices");
        // When
        worker(NOW).process(message);
        // Then
        assertThat(logsAtStage("worker_db_transaction")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("operation", "DEVICE_LOOKUP")
                        .containsEntry("transactionCompleted", true));
        assertThat(logsAtStage("worker_summary")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("targetCount", 0));
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("트랜잭션 시작에 실패해도 실패 시간과 구간을 남기고 예외를 전달한다")
    void process_transactionCannotStart_recordsFailureAndPropagates() {
        // Given
        PlatformTransactionManager unavailable = mock(PlatformTransactionManager.class);
        given(unavailable.getTransaction(any())).willThrow(new IllegalStateException("DB failed"));
        PushWorkerService failingWorker = new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                unavailable
        );
        // When & Then
        assertThatThrownBy(() -> failingWorker.process(message))
                .isInstanceOf(IllegalStateException.class);
        assertThat(logsAtStage("worker_db_transaction")).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("operation", "DEVICE_LOOKUP")
                    .containsEntry("transactionCompleted", false);
            assertThat((long) log.get("dbTransactionDurationMs")).isNotNegative();
        });
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("SDK 호출 중 기기 ID를 연결하고 성공·예외 모두 기존 MDC를 복원한다")
    void process_senderCompletesOrThrows_restoresDeviceContext(boolean fails) {
        // Given
        MDC.put("deviceId", "previous-device");
        MDC.put("sqsMessageId", "current-sqs");
        given(sender.send(any())).willAnswer(call -> {
            assertThat(MDC.get("deviceId")).isEqualTo(deviceId.toString());
            assertThat(MDC.get("sqsMessageId")).isEqualTo("current-sqs");
            if (fails) {
                throw new IllegalStateException("SDK failed");
            }
            return DevicePushResult.accepted();
        });
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isEqualTo(fails);
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "deviceId",
                        "previous-device",
                        "sqsMessageId",
                        "current-sqs"
                ));
    }

    @Test
    @DisplayName("같은 Worker의 두 동시 발송은 DB 계측과 기기 MDC를 각 메시지에 연결한다")
    void process_concurrentMessages_keepsSeparateTimingContexts() throws Exception {
        // Given
        PushWorkerService worker = worker(NOW);
        CountDownLatch bothSending = new CountDownLatch(2);
        given(sender.send(any())).willAnswer(call -> {
            bothSending.countDown();
            assertThat(bothSending.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(MDC.get("deviceId")).isEqualTo(deviceId.toString());
            return DevicePushResult.accepted();
        });
        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processWithContext(worker, "first-sqs"));
            var second = executor.submit(() -> processWithContext(worker, "second-sqs"));
            // Then
            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(
                    Map.entry("sqsMessageId", "first-sqs"));
            assertThat(second.get(10, TimeUnit.SECONDS)).containsExactly(
                    Map.entry("sqsMessageId", "second-sqs"));
        }
        assertThat(logsAtStage("worker_db_transaction")).hasSize(4)
                .allSatisfy(log -> assertThat((long) log.get("dbTransactionDurationMs"))
                        .isNotNegative());
        assertThat(logsAtStage("worker_db_transaction"))
                .extracting(log -> log.get("sqsMessageId"))
                .containsExactlyInAnyOrder(
                        "first-sqs",
                        "first-sqs",
                        "second-sqs",
                        "second-sqs"
                );
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("발생 후 30분 직전만 발송하고 정각·이후에는 완료한다")
    void process_deadlineBoundary_stopsAtDeadline(long seconds) {
        // When
        assertThat(worker(NOW.plusSeconds(seconds)).process(message).retryable()).isFalse();
        // Then
        if (seconds < 1800) {
            verify(sender).send(any());
            return;
        }
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @CsvSource({"read,ALREADY_READ", "postDeleted,NOTIFICATION_UNAVAILABLE",
            "withdrawn,LOGIN_UNUSABLE", "disabled,PUSH_DISABLED", "revoked,LOGIN_UNUSABLE",
            "expired,LOGIN_UNUSABLE", "missingDevice,NO_DEVICES",
            "missingNotification,NOTIFICATION_UNAVAILABLE", "missingPost,NOTIFICATION_UNAVAILABLE"})
    @DisplayName("발송 직전 읽음·삭제·탈퇴·설정·로그인·기기 상태를 확인하여 발송을 생략한다")
    void process_ineligibleLatestState_completesWithoutSending(String state, String reason) {
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
        if (state.equals("missingPost"))
            jdbc.update("DELETE FROM posts");
        // When & Then
        assertThat(worker(NOW).process(message).retryable()).isFalse();
        verifyNoInteractions(sender);
        assertThat(logsAtStage("worker_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", reason));
    }

    @Test
    @DisplayName("정지 회원에게도 검수 결과 푸시를 보낸다")
    void process_bannedUser_sendsModerationPush() {
        // Given
        jdbc.update("UPDATE users SET status='BANNED'");
        // When
        worker(NOW).process(message);
        // Then
        verify(sender).send(any());
    }

    @Test
    @DisplayName("모든 기기가 일시 실패하면 재전달을 요청하며 가장 긴 Retry-After를 유지한다")
    void process_allTransientFailures_retriesWithLongestDelay() {
        // Given
        addDevice("second-token");
        given(sender.send(any())).willReturn(
                DevicePushResult.retryable("UNAVAILABLE",
                        Duration.ofSeconds(60)),
                DevicePushResult.retryable("QUOTA",
                        Duration.ofSeconds(120)));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(logsAtStage("worker_db_transaction"))
                .filteredOn(log -> "LATEST_CONDITIONS".equals(log.get("operation")))
                .extracting(log -> log.get("deviceId")).doesNotHaveDuplicates().hasSize(2);
    }

    @Test
    @DisplayName("기기 하나라도 수락되면 나머지 일시 실패를 재시도하지 않는다")
    void process_partialAcceptance_completesUserMessage() {
        // Given
        addDevice("second-token");
        given(sender.send(any())).willReturn(
                DevicePushResult.retryable("UNAVAILABLE",
                        Duration.ofSeconds(60)),
                DevicePushResult.accepted());
        // When & Then
        assertThat(worker(NOW).process(message).retryable()).isFalse();
        verify(sender, times(2)).send(any());
        assertThat(logsAtStage("worker_summary")).singleElement().satisfies(log -> assertThat(log)
                .containsEntry("targetCount", 2).containsEntry("acceptedCount", 1)
                .containsEntry("retryableCount", 1).containsEntry("retryable", false));
    }

    @Test
    @DisplayName("첫 기기 수락 후 다음 기기의 예외가 발생해도 사용자 작업을 완료한다")
    void process_acceptedThenDeviceException_completesUserMessage() {
        // Given
        addDevice("second-token");
        given(sender.send(any()))
                .willReturn(DevicePushResult.accepted())
                .willThrow(new IllegalStateException("device processing failure"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
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
                .willReturn(DevicePushResult.accepted());
        // When
        PushProcessingResult result = worker(NOW).process(message);
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
        PushProcessingResult result = worker(NOW).process(message);
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
                DevicePushResult.accepted(),
                DevicePushResult.invalidToken("UNREGISTERED"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
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
        given(sender.send(any())).willReturn(DevicePushResult.invalidToken("UNREGISTERED"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
        assertThat(logsAtStage("worker_db_transaction"))
                .filteredOn(log -> "INVALID_TOKEN_CLEANUP".equals(log.get("operation")))
                .singleElement().satisfies(log -> {
                    assertThat(log).containsEntry("transactionCompleted", false)
                            .containsEntry("deviceId", deviceId);
                    assertThat((long) log.get("dbTransactionDurationMs")).isNotNegative();
                });
    }

    @Test
    @DisplayName("등록 해제 토큰의 해당 기기만 제거한다")
    void process_unregisteredToken_deletesAffectedDevice() {
        // Given
        UUID other = addDevice("other-token");
        given(sender.send(any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("worker-token"))
                return DevicePushResult.invalidToken("UNREGISTERED");
            return DevicePushResult.accepted();
        });
        // When
        worker(NOW).process(message);
        // Then
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(other);
        assertThat(logsAtStage("worker_db_transaction"))
                .filteredOn(log -> "INVALID_TOKEN_CLEANUP".equals(log.get("operation")))
                .singleElement().satisfies(log -> assertThat(log)
                        .containsEntry("transactionCompleted", true)
                        .containsEntry("deviceId", deviceId));
    }

    @Test
    @DisplayName("실패 응답 전에 갱신된 토큰은 이전 무효 토큰 제거로 삭제하지 않는다")
    void process_tokenUpdatedDuringSend_keepsNewToken() {
        // Given
        given(sender.send(any())).willAnswer(call -> {
            jdbc.update(
                    "UPDATE push_devices SET fcm_token='updated-token',fcm_token_hash=? WHERE id=?",
                    new FcmToken("updated-token").getHash(),
                    deviceId
            );
            return DevicePushResult.invalidToken("UNREGISTERED");
        });
        // When
        worker(NOW).process(message);
        // Then
        assertThat(jdbc.queryForObject(
                "SELECT fcm_token FROM push_devices WHERE id=?",
                String.class,
                deviceId
        )).isEqualTo("updated-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID_ARGUMENT", "SENDER_ID_MISMATCH", "THIRD_PARTY_AUTH_ERROR"})
    @DisplayName("메시지·프로젝트·인증 영구 오류는 기기와 토큰을 보존하고 작업을 완료한다")
    void process_permanentFailure_preservesDeviceAndCompletes(String errorCode) {
        // Given
        given(sender.send(any())).willReturn(DevicePushResult.permanentFailure(errorCode));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
        assertThat(jdbc.queryForObject(
                "SELECT fcm_token FROM push_devices WHERE id=?",
                String.class,
                deviceId
        )).isEqualTo("worker-token");
        assertThat(jdbc.queryForObject(
                "SELECT fcm_token_hash FROM push_devices WHERE id=?",
                String.class,
                deviceId
        )).isEqualTo(new FcmToken("worker-token").getHash());
    }

    @Test
    @DisplayName("다른 사건이나 임의로 연장한 기한의 메시지는 발송하지 않는다")
    void process_tamperedMessage_doesNotSend() {
        // Given
        PushMessage invalid = new PushMessage(
                UUID.randomUUID(),
                message.notificationId(),
                USER_ID,
                message.type(),
                message.sourceType(),
                POST_ID,
                NOW,
                NOW.plusSeconds(3600)
        );
        // When & Then
        assertThat(worker(NOW).process(invalid).retryable()).isFalse();
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 1})
    @DisplayName("발송 전용 조회에서도 원래 사건 시각보다 앞서거나 뒤선 메시지는 발송하지 않는다")
    void process_mismatchedOccurrenceTime_doesNotSend(long seconds) {
        // Given
        PushMessage invalid = new PushMessage(
                message.eventId(),
                message.notificationId(),
                USER_ID,
                message.type(),
                message.sourceType(),
                POST_ID,
                NOW.plusSeconds(seconds),
                message.expiresAt()
        );
        // When
        PushProcessingResult result = worker(NOW).process(invalid);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACCEPTED", "RETRYABLE", "PERMANENT_FAILURE", "INVALID_TOKEN",
            "SKIPPED"})
    @DisplayName("기기 처리 결과를 종류별 집계하고 식별 정보만 로그에 남긴다")
    void process_deviceResult_recordsCountsWithoutPrivateData(String status) {
        // Given
        DevicePushResult deviceResult = switch (status) {
            case "RETRYABLE" -> DevicePushResult.retryable("UNAVAILABLE");
            case "PERMANENT_FAILURE" -> DevicePushResult.permanentFailure("SENDER_ID_MISMATCH");
            case "INVALID_TOKEN" -> DevicePushResult.invalidToken("UNREGISTERED");
            case "SKIPPED" -> DevicePushResult.skipped("PUSH_EXPIRED");
            default -> DevicePushResult.accepted();
        };
        given(sender.send(any())).willReturn(deviceResult);
        // When
        worker(NOW).process(message);
        // Then
        Map<String, Object> summary = logsAtStage("worker_summary").getFirst();
        assertThat(summary).containsEntry("eventId", message.eventId())
                .containsEntry("notificationId", message.notificationId())
                .containsEntry("userId", USER_ID).containsEntry("targetCount", 1);
        assertThat(summary.get("acceptedCount")).isEqualTo(status.equals("ACCEPTED") ? 1 : 0);
        assertThat(summary.get("retryableCount")).isEqualTo(status.equals("RETRYABLE") ? 1 : 0);
        assertThat(summary.get("permanentFailureCount"))
                .isEqualTo(status.equals("PERMANENT_FAILURE") ? 1 : 0);
        assertThat(summary.get("invalidTokenCount"))
                .isEqualTo(status.equals("INVALID_TOKEN") ? 1 : 0);
        assertThat(summary.get("skippedCount")).isEqualTo(status.equals("SKIPPED") ? 1 : 0);
        assertThat(logsAtStage("fcm_result")).hasSize(status.equals("SKIPPED") ? 0 : 1);
        var request = org.mockito.ArgumentCaptor.forClass(DevicePushRequest.class);
        verify(sender).send(request.capture());
        assertThat(appender.list).allSatisfy(event -> {
            String logged = event.getFormattedMessage() + event.getKeyValuePairs();
            assertThat(logged).doesNotContain(
                    request.getValue().token(),
                    request.getValue().title(),
                    request.getValue().body(),
                    "worker@example.com",
                    "worker/original"
            );
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        return appender.list.stream().map(event -> {
            Map<String, Object> values = new HashMap<>(event.getMDCPropertyMap());
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

    private Map<String, String> processWithContext(PushWorkerService worker, String sqsMessageId) {
        MDC.put("sqsMessageId", sqsMessageId);
        try {
            worker.process(message);
            return MDC.getCopyOfContextMap();
        } finally {
            MDC.clear();
        }
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
                USER_ID,
                sessionId,
                new FcmToken(UUID.randomUUID().toString()).getHash(),
                Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW.plusSeconds(7200))
        );
        return new TransactionTemplate(manager).execute(status -> {
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
}
