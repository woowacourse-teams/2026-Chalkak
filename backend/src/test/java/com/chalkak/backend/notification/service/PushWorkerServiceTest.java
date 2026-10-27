package com.chalkak.backend.notification.service;

import com.chalkak.backend.notification.domain.PushDeviceDeliveryStatus;

import com.chalkak.backend.notification.domain.PushDeliveryStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.params.provider.CsvSource;
import com.chalkak.backend.common.logging.Elapsed;
import com.chalkak.backend.auth.service.token.LoginSessionService;
import com.chalkak.backend.notification.domain.FcmToken;
import com.chalkak.backend.notification.infrastructure.infra.SqsPushService;
import java.util.function.Consumer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import com.chalkak.backend.notification.domain.Notification;
import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;
import java.util.concurrent.atomic.AtomicInteger;

class PushWorkerServiceTest extends IntegrationTestSupport {
    @Autowired
    private PushDeliveryService deliveryService;
    @Autowired
    private PushDeliveryRepository deliveries;
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
    private final LoggerContext appenderContext = new LoggerContext();
    private String failingLogStage;
    private final AtomicInteger appenderFailureCount = new AtomicInteger();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            if (event.getKeyValuePairs() != null && event.getKeyValuePairs().stream()
                    .anyMatch(pair -> pair.key.equals("stage")
                            && pair.value.equals(failingLogStage))) {
                appenderFailureCount.incrementAndGet();
                throw new IllegalStateException("appender write failed");
            }
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
        appender.setContext(appenderContext);
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
        given(sender.send(any(), any())).willReturn(
                DevicePushResult.accepted());
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        appenderContext.stop();
        MDC.clear();
        jdbc.execute("DROP TRIGGER IF EXISTS worker_reject_device_delete ON push_devices");
        jdbc.execute("DROP FUNCTION IF EXISTS worker_reject_device_delete()");
        jdbc.execute("DROP TRIGGER IF EXISTS worker_reject_delivery_write ON push_deliveries");
        jdbc.execute("DROP FUNCTION IF EXISTS worker_reject_delivery_write()");
        new DatabaseCleaner(jdbc).clean();
    }

    @Test
    @DisplayName("현재 등록 기기로 보내며 최초 SQS 상태 반영이 실패한 대기 알림도 처리한다")
    void process_pendingNotification_sendsToCurrentDevice() {
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isFalse();
        verify(sender).send(any(), any());
        assertThat(logsAtStage("worker_db_transaction")).isEmpty();
        assertThat(logsAtStage("worker_summary")).singleElement().satisfies(log -> assertThat(log)
                .containsEntry("processingCompleted", true).containsEntry("dbTransactionCount", 2));
        assertThat(dbTransactions())
                .extracting(log -> log.get("operation"))
                .containsExactly("DEVICE_LOOKUP", "LATEST_CONDITIONS_LOOKUP");
        assertThat(dbTransactions()).allSatisfy(log -> {
            assertThat(log).containsEntry("transactionCompleted", true)
                    .containsEntry("eventId", message.eventId());
            assertThat((long) log.get("dbTransactionDurationMs")).isNotNegative();
        });
    }

    @Test
    @DisplayName("요약의 DB 상세는 JSON 배열로 기록되고 합계는 각 구간의 합과 같다")
    void process_dbSummary_encodesTransactionsAndCorrectTotal() {
        // Given
        MDC.put("sqsMessageId", "summary-sqs");
        MDC.put("receiveCount", "2");
        // When
        worker(NOW).process(message);
        // Then
        ILoggingEvent summary = appender.list.stream()
                .filter(event -> event.getKeyValuePairs().stream().anyMatch(
                        pair -> pair.key.equals("stage") && pair.value.equals("worker_summary")))
                .findFirst().orElseThrow();
        LoggerContext context = new LoggerContext();
        try {
            context.putObject(Environment.class.getName(), new MockEnvironment());
            StructuredLogEncoder encoder = new StructuredLogEncoder();
            encoder.setContext(context);
            encoder.setFormat("logstash");
            encoder.start();
            try {
                var json = JsonMapper.builder().build().readTree(encoder.encode(summary));
                assertThat(json.get("sqsMessageId").asText()).isEqualTo("summary-sqs");
                assertThat(json.get("receiveCount").asText()).isEqualTo("2");
                var transactions = json.get("dbTransactions");
                assertThat(transactions.isArray()).isTrue();
                assertThat(transactions.size()).isEqualTo(2);
                assertThat(transactions.get(0).get("deviceId").isNull()).isTrue();
                assertThat(transactions.get(1).get("deviceId").asText())
                        .isEqualTo(deviceId.toString());
                long sumMs = transactions.get(0).get("dbTransactionDurationMs").asLong()
                        + transactions.get(1).get("dbTransactionDurationMs").asLong();
                assertThat(json.get("dbTransactionDurationMs").asLong()).isEqualTo(sumMs);
                assertThat(json.get("dbTransactionCount").asInt()).isEqualTo(2);
            } finally {
                encoder.stop();
            }
        } finally {
            context.stop();
        }
    }

    @Test
    @DisplayName("DB 측정 시간에는 커밋 처리를 기다린 시간도 포함한다")
    void process_delayedCommit_includesTransactionCompletion() {
        // Given
        PlatformTransactionManager delayed = spy(manager);
        doAnswer(call -> {
            TimeUnit.MILLISECONDS.sleep(150);
            return call.callRealMethod();
        }).when(delayed).commit(any());
        PushWorkerService worker = worker(delayed);
        // When
        worker.process(message);
        // Then
        assertThat(dbTransactions()).hasSize(2).allSatisfy(log -> {
            assertThat(log).containsEntry("transactionCompleted", true);
            assertThat((long) log.get("dbTransactionDurationMs")).isGreaterThanOrEqualTo(100L);
        });
    }

    @Test
    @DisplayName("최신 조건 조회의 커밋이 실패하면 롤백 시간과 구간을 요약에 남긴다")
    void process_latestConditionsCommitFails_recordsRollbackAndRetries() {
        // Given
        AtomicInteger transactions = new AtomicInteger();
        PlatformTransactionManager failing = spy(manager);
        doAnswer(call -> {
            Object status = call.callRealMethod();
            if (transactions.incrementAndGet() == 2) {
                TransactionSynchronizationManager.registerSynchronization(
                        new TransactionSynchronization() {
                            @Override
                            public void beforeCommit(boolean readOnly) {
                                throw new IllegalStateException("conditions commit failed");
                            }
                        });
            }
            return status;
        }).when(failing).getTransaction(any());
        // When
        PushProcessingResult result = worker(failing).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(dbTransactions()).extracting(log -> log.get("transactionCompleted"))
                .containsExactly(true, false);
        assertThat(logsAtStage("worker_device_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("deviceId", deviceId)
                        .containsEntry("errorCode", "IllegalStateException"));
        verifyNoInteractions(sender);
    }

    @Test
    @DisplayName("DB 요약의 Logback 출력이 실패해도 기기 조회와 발송을 완료한다")
    void process_appenderFailure_completesDelivery() {
        // Given
        failingLogStage = "worker_summary";
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isFalse();
        assertThat(appenderFailureCount.get()).isEqualTo(1);
        verify(sender).send(any(), any());
    }

    @Test
    @DisplayName("기기가 없으면 목록 조회 시간과 0개 집계를 남기고 발송하지 않는다")
    void process_noDevices_recordsLookupWithoutSending() {
        // Given
        jdbc.update("DELETE FROM push_devices");
        // When
        worker(NOW).process(message);
        // Then
        assertThat(dbTransactions()).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("operation", "DEVICE_LOOKUP")
                        .containsEntry("transactionCompleted", true));
        assertThat(logsAtStage("worker_summary")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("targetCount", 0)
                        .containsEntry("dbTransactionCount", 1));
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
                unavailable,
                deliveryService
        );
        // When & Then
        assertThatThrownBy(() -> failingWorker.process(message))
                .isInstanceOf(IllegalStateException.class);
        assertThat(logsAtStage("worker_summary")).singleElement().satisfies(log -> assertThat(log)
                .containsEntry("processingCompleted", false)
                .containsEntry("errorCode", "IllegalStateException")
                .doesNotContainKeys(
                        "targetCount",
                        "acceptedCount",
                        "retryable"
                ));
        assertThat(dbTransactions()).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("operation", "DEVICE_LOOKUP")
                    .containsEntry("transactionCompleted", false);
            assertThat((long) log.get("dbTransactionDurationMs")).isNotNegative();
        });
        verifyNoInteractions(sender);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("발송 결과·예외에 SDK 시간을 합치고 기존 MDC는 변경하지 않는다")
    void process_senderCompletesOrThrows_combinesTimingWithoutChangingMdc(boolean fails) {
        // Given
        MDC.put("deviceId", "previous-device");
        MDC.put("sqsMessageId", "current-sqs");
        given(sender.send(any(), any())).willAnswer(call -> {
            assertThat(MDC.get("deviceId")).isEqualTo("previous-device");
            assertThat(MDC.get("sqsMessageId")).isEqualTo("current-sqs");
            recordSdkTiming(
                    call.getArgument(1),
                    17L,
                    !fails
            );
            if (fails) {
                throw new IllegalStateException("SDK failed");
            }
            return DevicePushResult.accepted();
        });
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isEqualTo(fails);
        String stage = "fcm_result";
        if (fails) {
            stage = "worker_device_failed";
        }
        assertThat(logsAtStage(stage)).singleElement().satisfies(log -> assertThat(log)
                .containsEntry("fcmRequestDurationMs", 17L).containsEntry("sdkAccepted", !fails)
                .containsEntry("deviceId", deviceId).containsEntry("sqsMessageId", "current-sqs"));
        assertThat(logsAtStage("fcm_request_completed")).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "deviceId",
                        "previous-device",
                        "sqsMessageId",
                        "current-sqs"
                ));
    }

    @Test
    @DisplayName("같은 Worker의 동시 발송은 서로 다른 측정 객체와 결과 로그를 사용한다")
    void process_concurrentMessages_keepsSeparateTimingContexts() throws Exception {
        // Given
        PushWorkerService worker = worker(NOW);
        PushMessage secondMessage = createAdditionalMessage();
        CountDownLatch bothSending = new CountDownLatch(2);
        List<DevicePushTiming> timings = new CopyOnWriteArrayList<>();
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushTiming timing = call.getArgument(1);
            timings.add(timing);
            long duration = 11L;
            if ("second-sqs".equals(MDC.get("sqsMessageId"))) {
                duration = 22L;
            }
            recordSdkTiming(
                    timing,
                    duration,
                    true
            );
            bothSending.countDown();
            assertThat(bothSending.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(MDC.get("deviceId")).isNull();
            return DevicePushResult.accepted();
        });
        // When
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> processWithContext(
                    worker,
                    "first-sqs",
                    message
            ));
            var second = executor.submit(() -> processWithContext(
                    worker,
                    "second-sqs",
                    secondMessage
            ));
            // Then
            assertThat(first.get(10, TimeUnit.SECONDS)).containsExactly(
                    Map.entry("sqsMessageId", "first-sqs"));
            assertThat(second.get(10, TimeUnit.SECONDS)).containsExactly(
                    Map.entry("sqsMessageId", "second-sqs"));
        }
        assertThat(timings).hasSize(2).doesNotHaveDuplicates();
        assertThat(logsAtStage("fcm_result")).hasSize(2);
        assertThat(logsAtStage("fcm_result"))
                .filteredOn(log -> "first-sqs".equals(log.get("sqsMessageId")))
                .singleElement()
                .satisfies(log -> assertThat(log).containsEntry("fcmRequestDurationMs", 11L));
        assertThat(logsAtStage("fcm_result"))
                .filteredOn(log -> "second-sqs".equals(log.get("sqsMessageId")))
                .singleElement()
                .satisfies(log -> assertThat(log).containsEntry("fcmRequestDurationMs", 22L));
        assertThat(dbTransactions()).hasSize(4)
                .allSatisfy(log -> assertThat((long) log.get("dbTransactionDurationMs"))
                        .isNotNegative());
        assertThat(dbTransactions())
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
            verify(sender).send(any(), any());
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
        assertThat(dbTransactions()).allSatisfy(log -> assertThat(log)
                .containsEntry("transactionCompleted", true));
        assertThat(logsAtStage("worker_summary")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("processingCompleted", true));
    }

    @Test
    @DisplayName("정지 회원에게도 검수 결과 푸시를 보낸다")
    void process_bannedUser_sendsModerationPush() {
        // Given
        jdbc.update("UPDATE users SET status='BANNED'");
        // When
        worker(NOW).process(message);
        // Then
        verify(sender).send(any(), any());
    }

    @Test
    @DisplayName("모든 기기가 일시 실패하면 재전달을 요청하며 가장 긴 Retry-After를 유지한다")
    void process_allTransientFailures_retriesWithLongestDelay() {
        // Given
        addDevice("second-token");
        given(sender.send(any(), any())).willReturn(
                DevicePushResult.retryable("UNAVAILABLE",
                        Duration.ofSeconds(60)),
                DevicePushResult.retryable("QUOTA",
                        Duration.ofSeconds(120)));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        assertThat(dbTransactions())
                .filteredOn(log -> "LATEST_CONDITIONS_LOOKUP".equals(log.get("operation")))
                .extracting(log -> log.get("deviceId")).doesNotHaveDuplicates().hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일부 기기가 수락돼도 일시 실패 기기만 재전달에서 다시 보내고 모두 종료되면 완료한다")
    void process_partialAcceptance_retriesOnlyFailedDevice(boolean firstDeviceFails) {
        // Given
        UUID other = addDevice("second-token");
        UUID failedDevice = other;
        UUID acceptedDevice = deviceId;
        String failedToken = "second-token";
        if (firstDeviceFails) {
            failedDevice = deviceId;
            acceptedDevice = other;
            failedToken = "worker-token";
        }
        String retryToken = failedToken;
        AtomicInteger failedAttempts = new AtomicInteger();
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals(retryToken) && failedAttempts.incrementAndGet() == 1) {
                return DevicePushResult.retryable("UNAVAILABLE", Duration.ofSeconds(120));
            }
            return DevicePushResult.accepted();
        });

        // When
        PushProcessingResult first = worker(NOW).process(message);

        // Then
        assertThat(first.retryable()).isTrue();
        assertThat(first.retryAfter()).isEqualTo(Duration.ofSeconds(120));
        verify(sender, times(2)).send(any(), any());
        assertThat(logsAtStage("worker_summary")).singleElement().satisfies(log -> assertThat(log)
                .containsEntry("targetCount", 2).containsEntry("acceptedCount", 1)
                .containsEntry("retryableCount", 1).containsEntry("retryable", true));
        PushDelivery pending = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(pending.getDeviceResults().get(acceptedDevice).isAccepted()).isTrue();
        assertThat(pending.getDeviceResults().get(failedDevice).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.RETRYABLE);

        PushProcessingResult repeated = worker(NOW.plusSeconds(120)).process(message);

        assertThat(repeated.retryable()).isFalse();
        verify(sender, times(3)).send(any(), any());
        PushDelivery completed = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getDeviceResults().get(acceptedDevice).getAttemptCount()).isEqualTo(1);
        assertThat(completed.getDeviceResults().get(failedDevice).getAttemptCount()).isEqualTo(2);
        assertThat(completed.getDeviceResults().values())
                .allMatch(PushDelivery.DeviceResult::isAccepted);
    }

    @Test
    @DisplayName("첫 기기 수락 후 다른 기기의 결과가 불명확하면 수락 이력을 보존하고 재처리한다")
    void process_acceptedThenDeviceException_preservesAcceptanceAndRetries() {
        // Given
        addDevice("second-token");
        given(sender.send(any(), any()))
                .willReturn(DevicePushResult.accepted())
                .willThrow(new IllegalStateException("device processing failure"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        verify(sender, times(2)).send(any(), any());
        PushDelivery pending = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(pending.isCompleted()).isFalse();
        assertThat(pending.getDeviceResults().values())
                .extracting(PushDelivery.DeviceResult::getStatus)
                .containsExactlyInAnyOrder(PushDeviceDeliveryStatus.ACCEPTED,
                        PushDeviceDeliveryStatus.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PERMANENT_FAILURE", "INVALID_TOKEN", "SKIPPED"})
    @DisplayName("수락·종료된 기기는 반복 발송하지 않고 일시 실패한 기기만 재처리한다")
    void process_mixedDeviceResults_retriesOnlyTransientFailure(String terminalStatus) {
        // Given
        UUID retryDevice = addDevice("retry-token");
        UUID terminalDevice = addDevice("terminal-token");
        AtomicInteger retryCalls = new AtomicInteger();
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("retry-token") && retryCalls.incrementAndGet() == 1) {
                return DevicePushResult.retryable("UNAVAILABLE");
            }
            if (request.token().equals("terminal-token")) {
                return switch (terminalStatus) {
                    case "INVALID_TOKEN" -> DevicePushResult.invalidToken("UNREGISTERED");
                    case "SKIPPED" -> DevicePushResult.skipped("EXPIRED");
                    default -> DevicePushResult.permanentFailure("INVALID_ARGUMENT");
                };
            }
            return DevicePushResult.accepted();
        });

        // When
        assertThat(worker(NOW).process(message).retryable()).isTrue();
        assertThat(worker(NOW.plusSeconds(60)).process(message).retryable()).isFalse();

        // Then
        verify(sender, times(4)).send(any(), any());
        PushDelivery completed = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(1);
        assertThat(completed.getDeviceResults().get(retryDevice).getAttemptCount()).isEqualTo(2);
        assertThat(completed.getDeviceResults().get(terminalDevice).getAttemptCount()).isEqualTo(1);
        assertThat(completed.getDeviceResults().get(terminalDevice).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.valueOf(terminalStatus));
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOGOUT", "PUSH_DISABLED", "READ", "DELETED", "WITHDRAWN"})
    @DisplayName("일시 실패 기기의 최신 발송 조건이 무효가 되면 재요청하지 않고 처리를 종료한다")
    void process_failedDeviceBecomesIneligible_completesWithoutResending(String changedState) {
        // Given
        UUID failed = addDevice("retry-token");
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("retry-token")) {
                return DevicePushResult.retryable("UNAVAILABLE");
            }
            return DevicePushResult.accepted();
        });
        assertThat(worker(NOW).process(message).retryable()).isTrue();
        switch (changedState) {
            case "LOGOUT" -> jdbc.update("""
                    DELETE FROM user_refresh_tokens
                    WHERE session_id = (SELECT session_id FROM push_devices WHERE id = ?)
                    """, failed);
            case "PUSH_DISABLED" -> jdbc.update("UPDATE users SET moderation_push_enabled=false");
            case "READ" -> jdbc.update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP");
            case "DELETED" -> jdbc.update("UPDATE posts SET deleted_at=CURRENT_TIMESTAMP");
            default -> jdbc.update("UPDATE users SET deleted_at=CURRENT_TIMESTAMP");
        }

        // When
        PushProcessingResult repeated = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(repeated.retryable()).isFalse();
        verify(sender, times(2)).send(any(), any());
        PushDelivery completed = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(completed.isCompleted()).isTrue();
        assertThat(completed.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(1);
        assertThat(completed.getDeviceResults().get(failed).getAttemptCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("부분 수락 후에도 실패 기기는 원래 30분 기한 직전까지만 새 요청을 시작한다")
    void process_partialAcceptanceAtDeadline_retriesOnlyBeforeDeadline(long seconds) {
        // Given
        addDevice("retry-token");
        AtomicInteger retryCalls = new AtomicInteger();
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("retry-token") && retryCalls.incrementAndGet() == 1) {
                return DevicePushResult.retryable("UNAVAILABLE");
            }
            return DevicePushResult.accepted();
        });
        assertThat(worker(NOW).process(message).retryable()).isTrue();

        // When
        PushProcessingResult repeated = worker(NOW.plusSeconds(seconds)).process(message);

        // Then
        assertThat(repeated.retryable()).isFalse();
        int expectedCalls = 2;
        if (seconds < 1800) {
            expectedCalls = 3;
        }
        verify(sender, times(expectedCalls)).send(any(), any());
    }

    @Test
    @DisplayName("첫 기기가 결과 불명이어도 다음 기기는 발송하고 미확정 기기는 재처리한다")
    void process_deviceExceptionThenAccepted_retriesUnknownDevice() {
        // Given
        addDevice("second-token");
        given(sender.send(any(), any()))
                .willThrow(new IllegalStateException("device processing failure"))
                .willReturn(DevicePushResult.accepted());
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result.retryable()).isTrue();
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("수락한 기기 없이 예상하지 못한 예외가 발생하면 재전달을 요청한다")
    void process_deviceExceptionWithoutAcceptance_retriesUserMessage() {
        // Given
        given(sender.send(any(), any()))
                .willThrow(new IllegalStateException("device processing failure"));
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
        given(sender.send(any(), any())).willReturn(
                DevicePushResult.accepted(),
                DevicePushResult.invalidToken("UNREGISTERED"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactlyInAnyOrder(deviceId, other);
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("무효 토큰 삭제 실패는 재발송 사유가 아니며 기기 행은 롤백되어 남는다")
    void process_invalidTokenCleanupFailure_preservesDeviceAndCompletes() {
        // Given
        rejectDeviceDeletion();
        given(sender.send(any(), any())).willReturn(DevicePushResult.invalidToken("UNREGISTERED"));
        // When
        PushProcessingResult result = worker(NOW).process(message);
        // Then
        assertThat(result).isEqualTo(PushProcessingResult.completed());
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(deviceId);
        assertThat(dbTransactions())
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
        given(sender.send(any(), any())).willAnswer(call -> {
            DevicePushRequest request = call.getArgument(0);
            if (request.token().equals("worker-token"))
                return DevicePushResult.invalidToken("UNREGISTERED");
            return DevicePushResult.accepted();
        });
        // When
        worker(NOW).process(message);
        // Then
        assertThat(devices.findIdsByUserId(USER_ID)).containsExactly(other);
        assertThat(dbTransactions())
                .filteredOn(log -> "INVALID_TOKEN_CLEANUP".equals(log.get("operation")))
                .singleElement().satisfies(log -> assertThat(log)
                        .containsEntry("transactionCompleted", true)
                        .containsEntry("deviceId", deviceId));
    }

    @Test
    @DisplayName("실패 응답 전에 갱신된 토큰은 이전 무효 토큰 제거로 삭제하지 않는다")
    void process_tokenUpdatedDuringSend_keepsNewToken() {
        // Given
        given(sender.send(any(), any())).willAnswer(call -> {
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
        given(sender.send(any(), any())).willReturn(DevicePushResult.permanentFailure(errorCode));
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
        given(sender.send(any(), any())).willAnswer(call -> {
            if (!status.equals("SKIPPED")) {
                recordSdkTiming(
                        call.getArgument(1),
                        23L,
                        status.equals("ACCEPTED")
                );
            }
            return deviceResult;
        });
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
        if (status.equals("SKIPPED")) {
            assertThat(logsAtStage("worker_skipped")).singleElement()
                    .satisfies(log -> assertThat(log)
                            .doesNotContainKeys("fcmRequestDurationMs", "sdkAccepted"));
        }
        if (!status.equals("SKIPPED")) {
            assertThat(logsAtStage("fcm_result")).singleElement().satisfies(log -> assertThat(log)
                    .containsEntry("fcmRequestDurationMs", 23L)
                    .containsEntry("sdkAccepted", status.equals("ACCEPTED")));
        }

        var request = org.mockito.ArgumentCaptor.forClass(DevicePushRequest.class);
        verify(sender).send(request.capture(), any());
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

    @Test
    @DisplayName("완료한 사건이 다시 오거나 새 기기가 등록돼도 FCM을 재호출하지 않는다")
    void process_completedDeliveryAndNewDevice_doesNotResend() {
        // Given
        worker(NOW).process(message);
        addDevice("newly-registered-token");

        // When
        PushProcessingResult repeated = worker(NOW.plusSeconds(10)).process(message);

        // Then
        assertThat(repeated.retryable()).isFalse();
        verify(sender).send(any(), any());
        PushDelivery saved = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(saved.isCompleted()).isTrue();
        assertThat(saved.getDeviceResults()).hasSize(1);
        assertThat(saved.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(1);
        assertThat(saved.getDeviceResults().get(deviceId).isAccepted()).isTrue();
    }

    @Test
    @DisplayName("명확한 일시 실패는 기록을 남기고 다음 수신에서만 다시 발송한다")
    void process_transientRedelivery_recordsAttemptsWithoutInternalRetryLoop() {
        // Given
        given(sender.send(any(), any())).willReturn(DevicePushResult.retryable("UNAVAILABLE"))
                .willReturn(DevicePushResult.accepted());

        // When
        PushProcessingResult first = worker(NOW).process(message);
        PushDelivery pending = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        PushProcessingResult second = worker(NOW.plusSeconds(60)).process(message);

        // Then
        assertThat(first.retryable()).isTrue();
        assertThat(pending.getStatus()).isEqualTo(PushDeliveryStatus.PENDING);
        assertThat(pending.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(1);
        assertThat(second.retryable()).isFalse();
        assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID).orElseThrow()
                .getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(2);
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("SDK 호출 중 예상하지 못한 예외는 결과 불명으로 기록하고 다음 수신에서 재처리한다")
    void process_unknownResult_preservesUncertaintyAndRetriesOnRedelivery() {
        // Given
        given(sender.send(any(), any())).willThrow(new IllegalStateException("SDK interrupted"))
                .willReturn(DevicePushResult.accepted());

        // When
        assertThat(worker(NOW).process(message).retryable()).isTrue();
        PushDelivery uncertain = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(worker(NOW.plusSeconds(60)).process(message).retryable()).isFalse();

        // Then
        assertThat(uncertain.getDeviceResults().get(deviceId).getStatus())
                .isEqualTo(PushDeviceDeliveryStatus.UNKNOWN);
        assertThat(uncertain.getDeviceResults().get(deviceId).getAcceptedAt()).isNull();
        assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID).orElseThrow()
                .getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(2);
        verify(sender, times(2)).send(any(), any());
    }

    @Test
    @DisplayName("동시에 같은 사건을 받아도 Java 락을 획득한 Worker만 FCM을 요청한다")
    void process_concurrentSameEvent_sendsOnlyFromLockOwner() throws Exception {
        // Given
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        given(sender.send(any(), any())).willAnswer(call -> {
            sending.countDown();
            assertThat(finish.await(5, TimeUnit.SECONDS)).isTrue();
            return DevicePushResult.accepted();
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var first = pool.submit(() -> worker(NOW).process(message));
            try {
                assertThat(sending.await(5, TimeUnit.SECONDS)).isTrue();

                // When
                PushProcessingResult other = worker(NOW).process(message);

                // Then
                assertThat(other.retryable()).isTrue();
                verify(sender).send(any(), any());
            } finally {
                finish.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).retryable()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"NOT_STARTED", "SENDING", "UNKNOWN", "RETRYABLE"})
    @DisplayName("수락 기록을 남기고 중단돼도 수락한 기기는 생략하고 미완료 기기를 이어 보낸다")
    void process_acceptedHistoryWithoutCompletion_recoversRemainingDevice(String previousStatus) {
        // Given
        PushDelivery claimed = deliveries.createIfAbsent(message.eventId(), USER_ID).orElseThrow();
        var accepted = claimed.startDevice(deviceId, NOW).finish(
                PushDeviceDeliveryStatus.ACCEPTED,
                null,
                NOW
        );
        deliveries.saveDeviceResult(
                claimed.getId(),
                deviceId,
                accepted
        );
        UUID remaining = addDevice("remaining-device-after-crash");
        if (!previousStatus.equals("NOT_STARTED")) {
            var previous = claimed.startDevice(remaining, NOW);
            if (!previousStatus.equals("SENDING")) {
                previous = previous.finish(
                        PushDeviceDeliveryStatus.valueOf(previousStatus),
                        "PREVIOUS_FAILURE",
                        NOW
                );
            }
            deliveries.saveDeviceResult(
                    claimed.getId(),
                    remaining,
                    previous
            );
        }

        // When: 프로세스 중단 뒤 남은 DB 기록만으로 다음 처리를 시작한다.
        assertThat(worker(NOW.plusSeconds(60)).process(message).retryable()).isFalse();

        // Then
        var requests = org.mockito.ArgumentCaptor.forClass(DevicePushRequest.class);
        verify(sender).send(requests.capture(), any());
        assertThat(requests.getValue().token()).isEqualTo("remaining-device-after-crash");
        PushDelivery recovered = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(recovered.isCompleted()).isTrue();
        assertThat(recovered.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(1);
        assertThat(recovered.getDeviceResults().get(remaining).isAccepted()).isTrue();
        int expectedAttempts = 2;
        if (previousStatus.equals("NOT_STARTED")) {
            expectedAttempts = 1;
        }
        assertThat(recovered.getDeviceResults().get(remaining).getAttemptCount())
                .isEqualTo(expectedAttempts);
    }

    @Test
    @DisplayName("로그아웃으로 기기 행을 삭제해도 완료 기록과 기기 결과는 유지한다")
    void process_deviceDeletedAfterAcceptance_preservesDeliveryHistory() {
        // Given
        worker(NOW).process(message);
        jdbc.update("DELETE FROM push_devices WHERE id=?", deviceId);

        // When
        PushDelivery saved = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();

        // Then
        assertThat(saved.isCompleted()).isTrue();
        assertThat(saved.getDeviceResults()).containsKey(deviceId);
    }

    @Test
    @DisplayName("최신 조건에서 생략한 잘못된 메시지는 정상 사건의 완료 기록을 선점하지 않는다")
    void process_invalidOccurrenceBeforeValidMessage_doesNotPoisonCompletion() {
        // Given
        PushMessage invalid = new PushMessage(
                message.eventId(),
                message.notificationId(),
                USER_ID,
                message.type(),
                message.sourceType(),
                POST_ID,
                NOW.plusSeconds(1),
                NOW.plusSeconds(1801)
        );

        // When
        assertThat(worker(NOW.plusSeconds(1)).process(invalid).retryable()).isFalse();
        assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)).isEmpty();
        assertThat(worker(NOW).process(message).retryable()).isFalse();

        // Then
        verify(sender).send(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("시작 기록 저장 실패 시 FCM을 호출하지 않고, 수락 결과 저장 실패 시 성공으로 확정하지 않는다")
    void process_deliveryWriteFailure_preservesUncertainty(boolean failAcceptance) {
        // Given
        jdbc.execute(
                """
                        CREATE FUNCTION worker_reject_delivery_write() RETURNS trigger LANGUAGE plpgsql AS $$
                        BEGIN
                            IF NOT TG_ARGV[0]::boolean OR
                                jsonb_path_exists(NEW.device_results, '$.* ? (@.status == "ACCEPTED")') THEN
                                RAISE EXCEPTION 'delivery write failed';
                            END IF;
                            RETURN NEW;
                        END $$
                        """);
        jdbc.execute("""
                CREATE TRIGGER worker_reject_delivery_write BEFORE UPDATE OF device_results
                ON push_deliveries FOR EACH ROW EXECUTE FUNCTION worker_reject_delivery_write('%s')
                """.formatted(failAcceptance));

        // When
        PushProcessingResult result = worker(NOW).process(message);

        // Then
        assertThat(result.retryable()).isTrue();
        if (failAcceptance) {
            PushDelivery saved = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                    .orElseThrow();
            assertThat(saved.isCompleted()).isFalse();
            verify(sender).send(any(), any());
            assertThat(logsAtStage("fcm_result")).singleElement()
                    .satisfies(log -> assertThat(log).containsEntry("result",
                            DevicePushResult.Status.ACCEPTED));
            assertThat(saved.getDeviceResults().get(deviceId).getStatus())
                    .isEqualTo(PushDeviceDeliveryStatus.SENDING);
            return;
        }
        verifyNoInteractions(sender);
        assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("SQS 삭제 실패 후 같은 메시지나 재발행 메시지를 받아도 FCM은 다시 보내지 않는다")
    void process_sqsDeletionFailsThenRedelivers_retriesOnlyDeletion(boolean republished) {
        // Given
        SqsClient sqs = mock(SqsClient.class);
        List<String> deletedReceipts = new CopyOnWriteArrayList<>();
        given(sqs.deleteMessage(
                org.mockito.ArgumentMatchers.<Consumer<DeleteMessageRequest.Builder>>any()))
                .willAnswer(call -> {
                    Consumer<DeleteMessageRequest.Builder> configure = call.getArgument(0);
                    DeleteMessageRequest.Builder request = DeleteMessageRequest.builder();
                    configure.accept(request);
                    deletedReceipts.add(request.build().receiptHandle());
                    assertThat(deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                            .orElseThrow().isCompleted()).isTrue();
                    if (deletedReceipts.size() == 1) {
                        throw new IllegalStateException("SQS delete unavailable");
                    }
                    return DeleteMessageResponse.builder().build();
                });
        ScheduledExecutorService visibilityExecutor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> visibilityTask = mock(ScheduledFuture.class);
        doReturn(visibilityTask).when(visibilityExecutor).scheduleAtFixedRate(
                any(Runnable.class),
                org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS)
        );
        SqsPushService sqsService = new SqsPushService(
                sqs,
                "https://sqs.ap-northeast-2.amazonaws.com/123456789012/test-push",
                JsonMapper.builder().build(),
                worker(NOW),
                Clock.fixed(NOW, ZoneOffset.UTC),
                visibilityExecutor
        );
        String nextMessageId = "message-first";
        String nextReceiveCount = "2";
        if (republished) {
            nextMessageId = "message-republished";
            nextReceiveCount = "1";
        }

        // When
        sqsService.process(sqsMessage(
                "message-first",
                "receipt-first",
                "1"
        ));
        assertThat(logsAtStage("worker_processing_failed")).hasSize(1);
        sqsService.process(sqsMessage(
                nextMessageId,
                "receipt-next",
                nextReceiveCount
        ));

        // Then
        verify(sender).send(any(), any());
        assertThat(deletedReceipts).containsExactly("receipt-first", "receipt-next");
        assertThat(logsAtStage("worker_delivery_skipped")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("reason", "ALREADY_COMPLETED"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("중단 후 SENDING만 남으면 재시도하고 수락 기록이 남으면 재발송 없이 완료한다")
    void process_restartWithPersistedAttempt_recoversFromDeviceResults(
            boolean acceptedBeforeCrash) {
        // Given: 프로세스를 실제로 종료하지 않고 중단 시 남는 DB 상태를 구성한다.
        PushDelivery previous = deliveries.createIfAbsent(message.eventId(), USER_ID).orElseThrow();
        PushDelivery.DeviceResult stored = previous.startDevice(deviceId, NOW);
        if (acceptedBeforeCrash) {
            stored = stored.finish(
                    PushDeviceDeliveryStatus.ACCEPTED,
                    null,
                    NOW
            );
        }
        assertThat(deliveries.saveDeviceResult(
                previous.getId(),
                deviceId,
                stored
        )).isTrue();
        // 새 서비스 객체에는 이전 프로세스의 Java 락이 남지 않는다.
        PushDeliveryService restartedDelivery = new PushDeliveryService(deliveries, manager);
        PushWorkerService restarted = new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC),
                manager,
                restartedDelivery
        );

        // When: DB에 수락이 남으면 생략하고 SENDING만 남으면 다시 보낸다.
        assertThat(restarted.process(message).retryable()).isFalse();

        // Then
        PushDelivery recovered = deliveries.findByEventIdAndUserId(message.eventId(), USER_ID)
                .orElseThrow();
        assertThat(recovered.isCompleted()).isTrue();
        assertThat(recovered.getDeviceResults().get(deviceId).isAccepted()).isTrue();
        if (acceptedBeforeCrash) {
            verifyNoInteractions(sender);
            assertThat(recovered.getDeviceResults().get(deviceId).getAttemptCount())
                    .isEqualTo(1);
            return;
        }
        verify(sender).send(any(), any());
        assertThat(recovered.getDeviceResults().get(deviceId).getAttemptCount()).isEqualTo(2);
    }

    private Message sqsMessage(
            String messageId,
            String receiptHandle,
            String receiveCount
    ) {
        return Message.builder()
                .messageId(messageId)
                .receiptHandle(receiptHandle)
                .body(JsonMapper.builder().build().writeValueAsString(message))
                .attributes(
                        Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, receiveCount))
                .build();
    }

    private PushMessage createAdditionalMessage() {
        return new TransactionTemplate(manager).execute(status -> {
            Notification notification = notifications.save(Notification.approved(
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
    }

    private void recordSdkTiming(
            DevicePushTiming timing,
            long durationMs,
            boolean accepted
    ) {
        Elapsed elapsed = mock(Elapsed.class);
        given(elapsed.toMillis()).willReturn(durationMs);
        timing.recordRequest(elapsed, accepted);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dbTransactions() {
        return logsAtStage("worker_summary").stream().flatMap(summary -> {
            var transactions = (List<Map<String, Object>>) summary.get("dbTransactions");
            return transactions.stream().map(transaction -> {
                Map<String, Object> values = new HashMap<>(summary);
                values.putAll(transaction);
                return values;
            });
        }).toList();
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
                manager,
                deliveryService
        );
    }

    private PushWorkerService worker(PlatformTransactionManager transactionManager) {
        return new PushWorkerService(
                notifications,
                topics,
                posts,
                reminders,
                devices,
                sessions,
                sender,
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager,
                deliveryService
        );
    }

    private Map<String, String> processWithContext(
            PushWorkerService worker,
            String sqsMessageId,
            PushMessage source
    ) {
        MDC.put("sqsMessageId", sqsMessageId);
        try {
            worker.process(source);
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
