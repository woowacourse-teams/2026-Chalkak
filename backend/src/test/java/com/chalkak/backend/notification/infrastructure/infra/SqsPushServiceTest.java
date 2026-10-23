package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushProcessingResult;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsPushServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final List<String> operations = new CopyOnWriteArrayList<>();
    private final List<JsonNode> bodies = new CopyOnWriteArrayList<>();
    private final AtomicReference<Runnable> heartbeatAction = new AtomicReference<>();
    private final CountDownLatch visibilityStarted = new CountDownLatch(1);
    private final CountDownLatch allowVisibilityResponse = new CountDownLatch(1);
    private HttpServer server;
    private ExecutorService serverExecutor;
    private ScheduledExecutorService visibilityExecutor;
    private SqsClient client;
    private SqsPushService worker;
    private PushWorkerService service;
    private ScheduledFuture<?> future;
    private PushMessage push;
    private Message message;
    private final Logger logger = (Logger) LoggerFactory
            .getLogger("com.chalkak.backend.notification");
    private Level previousLogLevel;
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
    private boolean failReceive;
    private boolean failVisibility;
    private boolean failDelete;
    private boolean holdVisibilityResponse;
    private String delayedVisibilityReceipt;
    private String receiveResponse = "{\"Messages\":[]}";

    @BeforeEach
    void setUp() throws Exception {
        previousLogLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        logger.addAppender(appender);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            String operation = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            operations.add(operation);
            JsonNode request = mapper.readTree(exchange.getRequestBody().readAllBytes());
            bodies.add(request);
            if (operation.endsWith("ChangeMessageVisibility")
                    && (holdVisibilityResponse || request.path("ReceiptHandle").asText()
                            .equals(delayedVisibilityReceipt))) {
                visibilityStarted.countDown();
                waitForVisibilityRelease();
            }
            int status = 200;
            String body = "{}";
            if (operation.endsWith("ReceiveMessage")) {
                body = receiveResponse;
                if (failReceive) {
                    status = 500;
                    body = "{\"__type\":\"InternalError\"}";
                }
            }
            if (operation.endsWith("DeleteMessage") && failDelete) {
                status = 500;
                body = "{\"__type\":\"InternalError\"}";
            }
            if (operation.endsWith("ChangeMessageVisibility") && failVisibility) {
                status = 500;
                body = "{\"__type\":\"InternalError\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = SqsClient.builder().endpointOverride(endpoint).region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create("test", "test")))
                .overrideConfiguration(config -> config
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
        visibilityExecutor = mock(ScheduledExecutorService.class);
        future = mock(ScheduledFuture.class);
        doAnswer(call -> {
            heartbeatAction.set(call.getArgument(0));
            return future;
        })
                .when(visibilityExecutor).scheduleAtFixedRate(
                        any(Runnable.class),
                        anyLong(),
                        anyLong(),
                        any(TimeUnit.class)
                );
        service = mock(PushWorkerService.class);
        given(service.process(any())).willReturn(PushProcessingResult.completed());
        worker = new SqsPushService(
                client,
                endpoint + "/000000000000/test",
                mapper,
                service,
                Clock.fixed(NOW, ZoneOffset.UTC),
                visibilityExecutor
        );
        push = new PushMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.POST_APPROVED,
                NotificationSourceType.POST,
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(1800)
        );
        message = Message.builder().messageId("sqs-id").receiptHandle("receipt-handle")
                .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "1"))
                .body(mapper.writeValueAsString(push)).build();
    }

    @AfterEach
    void tearDown() {
        logger.setLevel(previousLogLevel);
        logger.detachAppender(appender);
        appender.stop();
        MDC.clear();
        allowVisibilityResponse.countDown();
        client.close();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    @Test
    @DisplayName("재수신마다 SQS 식별자와 횟수를 발송 로그에 연결하고 기존 로그 문맥을 복원한다")
    void process_redeliveredMessage_correlatesSenderLogsAndRestoresContext() {
        // Given
        MDC.put("requestId", "previous-request");
        MDC.put("sqsMessageId", "previous-message");
        given(service.process(any())).willAnswer(call -> {
            LoggerFactory.getLogger(PushWorkerService.class).atInfo()
                    .addKeyValue("stage", "fcm_result").log("테스트 발송 결과");
            return PushProcessingResult.completed();
        });
        // When
        worker.process(message);
        worker.process(message.toBuilder().attributes(
                Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, "2")).build());
        // Then
        assertThat(logsAtStage("fcm_result")).hasSize(2);
        assertThat(logsAtStage("fcm_result").getFirst())
                .containsEntry("sqsMessageId", "sqs-id").containsEntry("receiveCount", "1");
        assertThat(logsAtStage("fcm_result").getLast())
                .containsEntry("sqsMessageId", "sqs-id").containsEntry("receiveCount", "2");
        assertThat(logsAtStage("worker_received").getFirst())
                .containsEntry("eventId", push.eventId()).containsEntry("userId", push.userId())
                .doesNotContainKey("sqsReceiveDurationMs");
        assertThat(MDC.getCopyOfContextMap()).containsExactlyInAnyOrderEntriesOf(
                Map.of(
                        "requestId",
                        "previous-request",
                        "sqsMessageId",
                        "previous-message"
                ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2"})
    @DisplayName("최초 수신과 재수신에서 원본 큐 입력부터 수신까지의 나이를 기록한다")
    void process_sentTimestamp_recordsAgeAndReceiveCount(String receiveCount) {
        // Given
        Message received = message.toBuilder().attributes(Map.of(
                MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT,
                receiveCount,
                MessageSystemAttributeName.SENT_TIMESTAMP,
                Long.toString(NOW.minusSeconds(5).toEpochMilli())
        )).build();
        // When
        worker.process(received);
        // Then
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> {
                    assertThat(log).containsEntry("queueAgeMs", 5000L)
                            .containsEntry("receiveCount", receiveCount)
                            .containsEntry("sqsMessageId", "sqs-id")
                            .containsEntry("outcome", "DELETED");
                    long total = (long) log.get("processingDurationMs");
                    long delete = (long) logsAtStage("worker_message_deleted").getFirst()
                            .get("sqsDeleteDurationMs");
                    assertThat(total).isGreaterThanOrEqualTo(delete);
                    assertThat(delete).isNotNegative();
                });
    }

    @Test
    @DisplayName("로그를 나중에 출력해도 큐 나이는 처리 종료가 아닌 수신 시각으로 계산한다")
    void process_clockAdvancesWhileProcessing_recordsAgeAtReceipt() {
        // Given
        AtomicLong wallClock = new AtomicLong(NOW.toEpochMilli());
        Clock advancingClock = mock(Clock.class);
        given(advancingClock.instant()).willAnswer(call -> Instant.ofEpochMilli(wallClock.get()));
        worker = new SqsPushService(
                client,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/000000000000/test",
                mapper,
                service,
                advancingClock,
                visibilityExecutor
        );
        Message received = message.toBuilder().attributes(Map.of(
                MessageSystemAttributeName.SENT_TIMESTAMP,
                Long.toString(NOW.minusSeconds(5).toEpochMilli())
        )).build();
        given(service.process(any())).willAnswer(call -> {
            wallClock.addAndGet(60_000);
            return PushProcessingResult.completed();
        });
        // When
        worker.process(received);
        // Then
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("queueAgeMs", 5000L)
                        .containsEntry("outcome", "DELETED"));
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "not-a-time", "-1", "9223372036854775807", "9223372036854775808"})
    @DisplayName("입력 시각이 누락·음수·미래·잘못된 숫자여도 나이를 0으로 꾸미지 않고 처리한다")
    void process_invalidSentTimestamp_completesWithUnknownAge(String sentTimestamp) {
        // Given
        Map<MessageSystemAttributeName, String> attributes = new HashMap<>(message.attributes());
        if (sentTimestamp != null) {
            attributes.put(MessageSystemAttributeName.SENT_TIMESTAMP, sentTimestamp);
        }
        // When
        worker.process(message.toBuilder().attributes(attributes).build());
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("queueAgeMs", null)
                        .containsEntry("outcome", "DELETED"));
        assertThat(logsAtStage("worker_processing_failed")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("수신 시각 직전·동일·직후의 입력 시각을 구분한다")
    void process_sentTimestampAroundReceipt_validatesAgeBoundary(long offsetMillis) {
        // Given
        Message received = message.toBuilder().attributes(Map.of(
                MessageSystemAttributeName.SENT_TIMESTAMP,
                Long.toString(NOW.toEpochMilli() + offsetMillis)
        )).build();
        // When
        worker.process(received);
        // Then
        Object age = logsAtStage("worker_attempt_completed").getFirst().get("queueAgeMs");
        if (offsetMillis > 0) {
            assertThat(age).isNull();
            return;
        }
        assertThat(age).isEqualTo(-offsetMillis);
    }

    @Test
    @DisplayName("메시지 파싱·처리 오류도 큐 식별자로 조사하고 원문·수신 핸들·예외 내용을 기록하지 않는다")
    void process_invalidAndFailedMessages_recordsSafeCorrelatedFailures() {
        // Given
        given(service.process(any()))
                .willThrow(new IllegalStateException("private-exception-secret"));
        // When
        worker.process(message.toBuilder().body("private-json-token-secret").build());
        worker.process(message);
        // Then
        assertThat(logsAtStage("worker_invalid_message")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("receiveCount", "1"));
        assertThat(logsAtStage("worker_processing_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("receiveCount", "1"));
        assertThat(logsAtStage("worker_attempt_completed"))
                .extracting(log -> log.get("outcome"))
                .containsExactly("INVALID_MESSAGE_DELETED", "FAILED");
        assertThat(logsAtStage("worker_attempt_completed")).allSatisfy(
                log -> assertThat((long) log.get("processingDurationMs")).isNotNegative());
        assertThat(MDC.get("sqsMessageId")).isNull();
        assertThat(MDC.get("receiveCount")).isNull();
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage() + event.getKeyValuePairs()
                    + event.getMDCPropertyMap())
                    .doesNotContain(
                            "private-json-token-secret",
                            "private-exception-secret",
                            "receipt-handle"
                    );
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    @DisplayName("전용 스레드의 숨김 연장 오류도 원래 큐 메시지와 연결한다")
    void process_visibilityFailureOnAnotherThread_recordsMessageId() {
        // Given
        failVisibility = true;
        given(service.process(any())).willAnswer(call -> {
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(heartbeatAction.get()).get(5, TimeUnit.SECONDS);
            }
            return PushProcessingResult.completed();
        });
        // When
        worker.process(message);
        // Then
        assertThat(logsAtStage("worker_visibility_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("receiveCount", "1"));
    }

    @Test
    @DisplayName("동시에 완료한 두 메시지는 각자의 수신 핸들로 삭제한다")
    void process_concurrentCompletions_deletesEachReceipt() throws Exception {
        // Given
        Message second = secondMessage();
        CountDownLatch bothProcessing = new CountDownLatch(2);
        given(service.process(any())).willAnswer(call -> {
            awaitBothMessages(bothProcessing);
            return PushProcessingResult.completed();
        });
        // When
        processConcurrently(second);
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage",
                "AmazonSQS.DeleteMessage");
        assertThat(bodies).extracting(body -> body.get("ReceiptHandle").asText())
                .containsExactlyInAnyOrder("receipt-handle", "second-receipt");
    }

    @Test
    @DisplayName("동시 처리 중 한 메시지가 실패해도 다른 메시지는 삭제한다")
    void process_oneConcurrentMessageFails_deletesOnlySuccessfulMessage() throws Exception {
        // Given
        Message second = secondMessage();
        CountDownLatch bothProcessing = new CountDownLatch(2);
        given(service.process(any())).willAnswer(call -> {
            awaitBothMessages(bothProcessing);
            PushMessage current = call.getArgument(0);
            if (!current.eventId().equals(push.eventId())) {
                throw new IllegalStateException("second message failed");
            }
            return PushProcessingResult.completed();
        });
        // When
        processConcurrently(second);
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        assertThat(bodies.getFirst().get("ReceiptHandle").asText()).isEqualTo("receipt-handle");
        assertThat(logsAtStage("worker_processing_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "second-sqs-id"));
    }

    @Test
    @DisplayName("동시 발송 로그는 각 메시지 식별자를 유지하고 처리 후 문맥을 비운다")
    void process_concurrentMessages_keepsSeparateLogContexts() throws Exception {
        // Given
        message = message.toBuilder().attributes(Map.of(
                MessageSystemAttributeName.SENT_TIMESTAMP,
                Long.toString(NOW.minusSeconds(1).toEpochMilli())
        )).build();
        Message second = secondMessage().toBuilder().attributes(Map.of(
                MessageSystemAttributeName.SENT_TIMESTAMP,
                Long.toString(NOW.minusSeconds(3).toEpochMilli())
        )).build();
        PushMessage secondPush = mapper.readValue(second.body(), PushMessage.class);
        CountDownLatch bothProcessing = new CountDownLatch(2);
        given(service.process(any())).willAnswer(call -> {
            awaitBothMessages(bothProcessing);
            PushMessage current = call.getArgument(0);
            LoggerFactory.getLogger(PushWorkerService.class).atInfo()
                    .addKeyValue("stage", "fcm_result").addKeyValue("eventId", current.eventId())
                    .log("동시 처리 테스트");
            return PushProcessingResult.completed();
        });
        // When
        List<Map<String, String>> remainingContexts = processConcurrently(second);
        // Then
        assertThat(remainingContexts).allSatisfy(context -> assertThat(context).isEmpty());
        assertThat(logsAtStage("fcm_result")).hasSize(2)
                .anySatisfy(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("eventId", push.eventId()))
                .anySatisfy(log -> assertThat(log).containsEntry("sqsMessageId", "second-sqs-id")
                        .containsEntry("eventId", secondPush.eventId()));
        assertThat(logsAtStage("worker_attempt_completed")).hasSize(2)
                .allSatisfy(log -> {
                    assertThat(log).containsEntry("outcome", "DELETED");
                    assertThat((long) log.get("processingDurationMs")).isNotNegative();
                })
                .anySatisfy(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("queueAgeMs", 1000L))
                .anySatisfy(log -> assertThat(log).containsEntry("sqsMessageId", "second-sqs-id")
                        .containsEntry("queueAgeMs", 3000L));
    }

    @Test
    @DisplayName("한 메시지가 완료되어도 아직 처리 중인 다른 메시지의 숨김 연장은 유지한다")
    void process_oneMessageCompletes_extendsOnlyRemainingMessage() throws Exception {
        // Given
        Message second = secondMessage();
        Map<String, Runnable> extensions = captureExtensions();
        CountDownLatch bothProcessing = new CountDownLatch(2);
        CountDownLatch releaseSecond = new CountDownLatch(1);
        given(service.process(any())).willAnswer(call -> {
            awaitBothMessages(bothProcessing);
            PushMessage current = call.getArgument(0);
            if (!current.eventId().equals(push.eventId())) {
                assertThat(releaseSecond.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return PushProcessingResult.completed();
        });
        try (var processors = Executors.newFixedThreadPool(2)) {
            var firstProcessing = processors.submit(() -> worker.process(message));
            var secondProcessing = processors.submit(() -> worker.process(second));
            try {
                // When
                firstProcessing.get(5, TimeUnit.SECONDS);
                extensions.get("sqs-id").run();
                extensions.get("second-sqs-id").run();
                // Then
                assertThat(secondProcessing.isDone()).isFalse();
                assertThat(operations).containsExactly("AmazonSQS.DeleteMessage",
                        "AmazonSQS.ChangeMessageVisibility");
                assertThat(bodies.getLast().get("ReceiptHandle").asText())
                        .isEqualTo("second-receipt");
                releaseSecond.countDown();
                secondProcessing.get(5, TimeUnit.SECONDS);
                extensions.values().forEach(Runnable::run);
                assertThat(operations).containsExactly(
                        "AmazonSQS.DeleteMessage",
                        "AmazonSQS.ChangeMessageVisibility",
                        "AmazonSQS.DeleteMessage"
                );
            } finally {
                releaseSecond.countDown();
            }
        }
    }

    @Test
    @DisplayName("첫 숨김 연장 요청이 응답하지 않아도 타임아웃 후 같은 스레드가 다음 메시지를 연장한다")
    void process_visibilityRequestStalls_singleExecutorContinuesAfterTimeout() throws Exception {
        // Given
        delayedVisibilityReceipt = "receipt-handle";
        Message second = secondMessage();
        Map<String, Runnable> extensions = captureExtensions();
        CountDownLatch bothProcessing = new CountDownLatch(2);
        CountDownLatch finishProcessing = new CountDownLatch(1);
        given(service.process(any())).willAnswer(call -> {
            bothProcessing.countDown();
            assertThat(finishProcessing.await(15, TimeUnit.SECONDS)).isTrue();
            return PushProcessingResult.completed();
        });
        try (var processors = Executors.newFixedThreadPool(2);
                var extensionThread = Executors.newSingleThreadExecutor()) {
            var firstProcessing = processors.submit(() -> worker.process(message));
            var secondProcessing = processors.submit(() -> worker.process(second));
            try {
                assertThat(bothProcessing.await(3, TimeUnit.SECONDS)).isTrue();
                // When: 40초 주기를 기다리지 않고 두 연장 작업을 같은 실행기에 순서대로 넣는다.
                var firstExtension = extensionThread.submit(extensions.get("sqs-id"));
                assertThat(visibilityStarted.await(3, TimeUnit.SECONDS)).isTrue();
                var secondExtension = extensionThread.submit(extensions.get("second-sqs-id"));
                firstExtension.get(10, TimeUnit.SECONDS);
                secondExtension.get(3, TimeUnit.SECONDS);
                // Then: 서버는 첫 응답을 아직 반환하지 않았지만 SDK 요청 기한으로 다음 작업이 진행된다.
                assertThat(allowVisibilityResponse.getCount()).isEqualTo(1);
                assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility",
                        "AmazonSQS.ChangeMessageVisibility");
                assertThat(bodies.getLast().get("ReceiptHandle").asText())
                        .isEqualTo("second-receipt");
                assertThat(logsAtStage("worker_visibility_failed")).singleElement()
                        .satisfies(log -> {
                            assertThat(log).containsEntry("sqsMessageId", "sqs-id");
                            assertThat(log.get("errorCode").toString()).contains("Timeout");
                        });
            } finally {
                allowVisibilityResponse.countDown();
                finishProcessing.countDown();
            }
            firstProcessing.get(5, TimeUnit.SECONDS);
            secondProcessing.get(5, TimeUnit.SECONDS);
        }
    }

    private Message secondMessage() {
        PushMessage secondPush = new PushMessage(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationType.POST_REJECTED,
                NotificationSourceType.POST,
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(1800)
        );
        return message.toBuilder().messageId("second-sqs-id").receiptHandle("second-receipt")
                .body(mapper.writeValueAsString(secondPush)).build();
    }

    private void awaitBothMessages(CountDownLatch bothProcessing) throws InterruptedException {
        bothProcessing.countDown();
        assertThat(bothProcessing.await(5, TimeUnit.SECONDS)).isTrue();
    }

    private List<Map<String, String>> processConcurrently(Message second) throws Exception {
        try (var processors = Executors.newFixedThreadPool(2)) {
            var firstResult = processors.submit(() -> processAndReadContext(message));
            var secondResult = processors.submit(() -> processAndReadContext(second));
            return List.of(firstResult.get(5, TimeUnit.SECONDS),
                    secondResult.get(5, TimeUnit.SECONDS));
        }
    }

    private Map<String, String> processAndReadContext(Message target) {
        worker.process(target);
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context == null ? Map.of() : context;
    }

    private Map<String, Runnable> captureExtensions() {
        Map<String, Runnable> extensions = new ConcurrentHashMap<>();
        doAnswer(call -> {
            extensions.put(MDC.get("sqsMessageId"), call.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(visibilityExecutor).scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                any(TimeUnit.class)
        );
        return extensions;
    }

    private List<Map<String, Object>> logsAtStage(String stage) {
        return appender.list.stream().map(event -> {
            Map<String, Object> values = new HashMap<>(event.getMDCPropertyMap());
            event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
            return values;
        }).filter(values -> stage.equals(values.get("stage"))).toList();
    }

    private void waitForVisibilityRelease() throws IOException {
        try {
            if (!allowVisibilityResponse.await(15, TimeUnit.SECONDS)) {
                throw new IOException("Test visibility response timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Test visibility response interrupted", exception);
        }
    }

    @Test
    @DisplayName("알림함 ID가 없는 주제 JSON도 공통 Worker에 전달하고 완료 결과이면 SQS에서 삭제한다")
    void process_topicMessageWithoutInboxId_passesToWorkerAndDeletes() throws Exception {
        // Given
        PushMessage topic = new PushMessage(
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                NotificationType.TOPIC,
                NotificationSourceType.TOPIC,
                UUID.randomUUID(),
                NOW,
                NOW.plusSeconds(1800)
        );
        Message topicMessage = Message.builder().messageId("topic-sqs-id")
                .receiptHandle("topic-receipt").body(mapper.writeValueAsString(topic)).build();
        // When
        worker.process(topicMessage);
        // Then
        verify(service).process(topic);
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
    }

    @Test
    @DisplayName("수신은 20초 롱 폴링·60초 숨김·한 건으로 요청한다")
    void poll_emptyQueue_requestsLongPollingAndVisibility() {
        // When
        worker.poll();
        // Then
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage");
        assertThat(bodies.getFirst().get("WaitTimeSeconds").asInt()).isEqualTo(20);
        assertThat(bodies.getFirst().get("VisibilityTimeout").asInt()).isEqualTo(60);
        assertThat(bodies.getFirst().get("MaxNumberOfMessages").asInt()).isEqualTo(1);
        assertThat(bodies.getFirst().path("MessageSystemAttributeNames"))
                .containsExactly(mapper.valueToTree("ApproximateReceiveCount"),
                        mapper.valueToTree("SentTimestamp"));
        assertThat(logsAtStage("worker_sqs_receive")).isEmpty();
        assertThat(logsAtStage("worker_attempt_completed")).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("DEBUG에서만 빈 수신 응답의 시간과 메타데이터를 기록한다")
    void poll_emptyQueueWithDebug_recordsReceiveTiming() {
        // Given
        logger.setLevel(Level.DEBUG);
        MDC.put("sqsMessageId", "previous-message");
        MDC.put("receiveCount", "previous-count");
        // When
        worker.poll();
        // Then
        assertThat(appender.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.DEBUG));
        assertThat(logsAtStage("worker_sqs_receive")).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("received", false).containsEntry("sqsMessageId", null)
                    .containsEntry("receiveCount", null);
            assertThat((long) log.get("sqsReceiveDurationMs")).isNotNegative();
        });
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"worker_received", "worker_message_deleted",
            "worker_attempt_completed"})
    @DisplayName("계측 로그가 실패해도 메시지 처리·삭제와 MDC 정리를 완료하며 폴링 오류로 바꾸지 않는다")
    void poll_timingLogFailure_completesWithoutPollingFailure(String stage) throws Exception {
        // Given
        failingLogStage = stage;
        receiveOneMessage();
        MDC.put("requestId", "previous-request");
        // When
        worker.poll();
        // Then
        verify(service).process(push);
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage");
        assertThat(logsAtStage("worker_poll_failed")).isEmpty();
        assertThat(logsAtStage("worker_processing_failed")).isEmpty();
        assertThat(MDC.getCopyOfContextMap()).containsExactlyEntriesOf(
                Map.of("requestId", "previous-request"));
    }

    @Test
    @DisplayName("수신 오류가 나면 최소 1초 대기하고 다음 폴링을 계속할 수 있다")
    void poll_receiveFailure_waitsBeforeNextPoll() {
        // Given
        failReceive = true;
        long started = System.nanoTime();
        // When
        worker.poll();
        long elapsed = System.nanoTime() - started;
        failReceive = false;
        worker.poll();
        // Then
        assertThat(elapsed).isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.ReceiveMessage");
        assertThat(logsAtStage("worker_poll_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("phase", "RECEIVE"));
        assertThat((long) logsAtStage("worker_poll_failed").getFirst()
                .get("sqsReceiveDurationMs")).isNotNegative();
        assertThat(logsAtStage("worker_attempt_completed")).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("처리 진입 실패 시 확정된 수신 시간을 오류 로그에 보존하고 다음 폴링을 계속한다")
    void poll_processingEntryFailure_preservesReceiveTimingAndContinues() throws Exception {
        // Given
        worker = spy(worker);
        doThrow(new IllegalStateException("processing entry failed")).doCallRealMethod()
                .when(worker).process(any(), any());
        receiveOneMessage();
        long startedAt = System.nanoTime();
        // When & Then
        assertThatCode(worker::poll).doesNotThrowAnyException();
        assertThat(System.nanoTime() - startedAt)
                .isGreaterThanOrEqualTo(TimeUnit.SECONDS.toNanos(1));
        assertThat(logsAtStage("worker_poll_failed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("phase", "PROCESSING")
                        .containsEntry("sqsMessageId", "sqs-id")
                        .containsKey("sqsReceiveDurationMs"));
        worker.poll();
        assertThat(operations).containsExactly(
                "AmazonSQS.ReceiveMessage",
                "AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage"
        );
        verify(service).process(push);
    }

    @Test
    @DisplayName("수신 시각 계측이 실패해도 메시지는 처리·삭제하고 큐 나이만 생략한다")
    void poll_receiptClockFailure_completesWithoutQueueAge() throws Exception {
        // Given
        Clock failingClock = mock(Clock.class);
        given(failingClock.instant()).willThrow(new IllegalStateException("clock failed"));
        worker = new SqsPushService(
                client,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/000000000000/test",
                mapper,
                service,
                failingClock,
                visibilityExecutor
        );
        receiveOneMessage();
        // When
        worker.poll();
        // Then
        verify(service).process(push);
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage");
        assertThat(logsAtStage("worker_poll_failed")).isEmpty();
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("outcome", "DELETED")
                        .containsEntry("queueAgeMs", null));
    }

    @Test
    @DisplayName("수신 오류 대기에 종료 신호가 들어오면 인터럽트 상태를 유지한다")
    void poll_interruptedFailureWait_preservesInterrupt() throws Exception {
        // Given
        SqsClient failingClient = mock(SqsClient.class);
        given(failingClient.receiveMessage(
                any(Consumer.class)))
                .willAnswer(call -> {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("receive interrupted");
                });
        SqsPushService interruptedWorker = new SqsPushService(
                failingClient,
                "test",
                mapper,
                service,
                Clock.fixed(NOW, ZoneOffset.UTC),
                mock(ScheduledExecutorService.class)
        );
        FutureTask<Boolean> polling = new FutureTask<>(() -> {
            interruptedWorker.poll();
            return Thread.currentThread().isInterrupted();
        });
        // When
        Thread.ofPlatform().start(polling);
        // Then
        assertThat(polling.get(3, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("메시지를 한 건 받으면 처리 후 해당 메시지를 삭제한다")
    void poll_oneMessage_processesAndDeletesMessage() throws Exception {
        // Given
        receiveOneMessage();
        // When
        worker.poll();
        // Then
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage");
        assertThat(appender.list.getFirst().getLevel()).isEqualTo(Level.INFO);
        assertThat(bodies.getLast().get("ReceiptHandle").asText()).isEqualTo("receipt-handle");
        assertThat(logsAtStage("worker_sqs_receive")).isEmpty();
        assertThat(logsAtStage("worker_received")).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("received", true).containsEntry("messageParsed", true)
                    .containsEntry("sqsMessageId", "sqs-id")
                    .containsEntry("receiveCount", "1").containsEntry("eventId", push.eventId())
                    .containsEntry("notificationId", push.notificationId())
                    .containsEntry("userId", push.userId());
            assertThat((long) log.get("sqsReceiveDurationMs")).isNotNegative();
        });
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("outcome", "DELETED"));
    }

    @Test
    @DisplayName("메시지 해석에 걸린 시간은 확정된 수신 API 시간에 포함하지 않는다")
    void poll_slowMessageParsing_keepsReceiveTimeSeparate() throws Exception {
        // Given
        ObjectMapper slowMapper = spy(mapper);
        doAnswer(call -> {
            TimeUnit.MILLISECONDS.sleep(250);
            return mapper.readValue(call.getArgument(0, String.class), PushMessage.class);
        }).when(slowMapper).readValue(any(String.class), eq(PushMessage.class));
        worker = new SqsPushService(
                client,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/000000000000/test",
                slowMapper,
                service,
                Clock.fixed(NOW, ZoneOffset.UTC),
                visibilityExecutor
        );
        receiveOneMessage();
        long startedAt = System.nanoTime();
        // When
        worker.poll();
        long totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        // Then
        long receiveMs = (long) logsAtStage("worker_received").getFirst()
                .get("sqsReceiveDurationMs");
        assertThat(totalMs - receiveMs).isGreaterThanOrEqualTo(200L);
        assertThat((long) logsAtStage("worker_attempt_completed").getFirst()
                .get("processingDurationMs")).isGreaterThanOrEqualTo(200L);
        verify(service).process(push);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "null"})
    @DisplayName("메시지 해석 실패·JSON null도 수신 식별자와 시간을 한 번 남기고 삭제한다")
    void poll_invalidMessage_preservesReceiveTiming(String body) throws Exception {
        // Given
        message = message.toBuilder().body(body).build();
        receiveOneMessage();
        // When
        worker.poll();
        // Then
        assertThat(logsAtStage("worker_received")).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("received", true).containsEntry("messageParsed", false)
                    .containsEntry("sqsMessageId", "sqs-id").containsEntry("receiveCount", "1")
                    .doesNotContainKey("eventId");
            assertThat((long) log.get("sqsReceiveDurationMs")).isNotNegative();
        });
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log)
                        .containsEntry("outcome", "INVALID_MESSAGE_DELETED"));
        assertThat(logsAtStage("worker_sqs_receive")).isEmpty();
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("숨김 연장 예약부터 실패해도 수신 시간은 오류 로그에 한 번 남긴다")
    void poll_visibilitySchedulingFails_preservesReceiveTiming() throws Exception {
        // Given
        given(visibilityExecutor.scheduleAtFixedRate(
                any(Runnable.class),
                anyLong(),
                anyLong(),
                any(TimeUnit.class)
        )).willThrow(new java.util.concurrent.RejectedExecutionException("executor stopped"));
        receiveOneMessage();
        // When
        worker.poll();
        // Then
        assertThat(logsAtStage("worker_received")).isEmpty();
        assertThat(logsAtStage("worker_processing_failed")).singleElement().satisfies(log -> {
            assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                    .containsEntry("errorCode", "RejectedExecutionException");
            assertThat((long) log.get("sqsReceiveDurationMs")).isNotNegative();
        });
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("outcome", "FAILED"));
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage");
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("처리 도중 실패하면 이미 남긴 수신 시간을 오류 로그에 중복 기록하지 않는다")
    void poll_processingFails_recordsReceiveTimingOnlyOnce() throws Exception {
        // Given
        given(service.process(any())).willThrow(new IllegalStateException("processing failed"));
        receiveOneMessage();
        // When
        worker.poll();
        // Then
        assertThat(logsAtStage("worker_received")).singleElement()
                .satisfies(log -> assertThat(log).containsKey("sqsReceiveDurationMs"));
        assertThat(logsAtStage("worker_processing_failed")).singleElement()
                .satisfies(log -> assertThat(log).doesNotContainKey("sqsReceiveDurationMs"));
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage");
    }

    private void receiveOneMessage() throws Exception {
        receiveResponse = mapper.writeValueAsString(Map.of("Messages", List.of(Map.of(
                "MessageId",
                message.messageId(),
                "ReceiptHandle",
                message.receiptHandle(),
                "Attributes",
                message.attributesAsStrings(),
                "MD5OfBody",
                HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                        .digest(message.body().getBytes(StandardCharsets.UTF_8))),
                "Body",
                message.body()
        ))));
    }

    @Test
    @DisplayName("완료 결과는 수신 핸들로 메시지를 삭제하고 숨김 연장을 멈춘다")
    void process_completed_deletesAndCancelsHeartbeat() {
        // When
        worker.process(message);
        heartbeatAction.get().run();
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        assertThat(bodies.getFirst().get("ReceiptHandle").asText()).isEqualTo("receipt-handle");
        verify(future, atLeastOnce()).cancel(false);
    }

    @Test
    @DisplayName("처리 중에는 숨김 시간을 연장하고 일시 실패 후에는 연장을 멈춘다")
    void process_processingAndTransientFailure_extendsOnlyWhileProcessing() {
        // Given
        given(service.process(any())).willAnswer(call -> {
            heartbeatAction.get().run();
            return new PushProcessingResult(true, Duration.ZERO);
        });
        // When
        worker.process(message);
        heartbeatAction.get().run();
        // Then
        assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility");
        assertThat(bodies.getFirst().get("VisibilityTimeout").asInt()).isEqualTo(60);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("진행 중인 숨김 연장이 끝난 뒤 삭제 또는 재시도 지연을 적용한다")
    void process_completionDuringHeartbeat_waitsBeforeFinalSqsRequest(boolean retryable)
            throws Exception {
        // Given
        holdVisibilityResponse = true;
        CountDownLatch deliveryCompleted = new CountDownLatch(1);
        FutureTask<Void> extension = new FutureTask<>(() -> heartbeatAction.get().run(), null);
        given(service.process(any())).willAnswer(call -> {
            Thread.ofPlatform().start(extension);
            assertThat(visibilityStarted.await(3, TimeUnit.SECONDS)).isTrue();
            deliveryCompleted.countDown();
            return new PushProcessingResult(retryable, Duration.ofSeconds(120));
        });
        FutureTask<Void> processing = new FutureTask<>(() -> worker.process(message), null);
        Thread processingThread = Thread.ofPlatform().unstarted(processing);
        // When & Then
        try {
            processingThread.start();
            assertThat(deliveryCompleted.await(3, TimeUnit.SECONDS)).isTrue();
            assertTimeoutPreemptively(Duration.ofSeconds(2),
                    () -> {
                        while (processingThread.getState() != Thread.State.BLOCKED
                                && !Thread.currentThread().isInterrupted()) {
                            Thread.onSpinWait();
                        }
                        assertThat(processingThread.getState()).isEqualTo(Thread.State.BLOCKED);
                    });
            assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility");
            allowVisibilityResponse.countDown();
            processing.get(3, TimeUnit.SECONDS);
            extension.get(3, TimeUnit.SECONDS);
            assertThat(bodies.getFirst().get("VisibilityTimeout").asInt()).isEqualTo(60);
            if (retryable) {
                assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility",
                        "AmazonSQS.ChangeMessageVisibility");
                assertThat(bodies.getLast().get("VisibilityTimeout").asInt()).isEqualTo(121);
            } else {
                assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility",
                        "AmazonSQS.DeleteMessage");
            }
            heartbeatAction.get().run();
            assertThat(operations).hasSize(2);
        } finally {
            allowVisibilityResponse.countDown();
            processing.get(3, TimeUnit.SECONDS);
            extension.get(3, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("Retry-After가 있으면 삭제 없이 다음 전달을 지연한다")
    void process_retryAfter_changesVisibilityWithoutDeleting() {
        // Given
        given(service.process(any()))
                .willReturn(new PushProcessingResult(true, Duration.ofSeconds(120)));
        // When
        worker.process(message);
        // Then
        assertThat(operations).containsExactly("AmazonSQS.ChangeMessageVisibility");
        assertThat(bodies.getFirst().get("VisibilityTimeout").asInt()).isEqualTo(121);
        assertThat(logsAtStage("worker_retry_pending")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("sqsMessageId", "sqs-id")
                        .containsEntry("retryAfterSeconds", 120L));
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("outcome", "RETRY_PENDING"));
        assertThat(logsAtStage("worker_message_deleted")).isEmpty();
    }

    @Test
    @DisplayName("재시도 지연은 사건의 원래 기한을 넘기지 않는다")
    void process_delayBeyondDeadline_capsVisibilityAtDeadline() {
        // Given
        given(service.process(any()))
                .willReturn(new PushProcessingResult(true, Duration.ofSeconds(3600)));
        // When
        worker.process(message);
        // Then
        assertThat(bodies.getFirst().get("VisibilityTimeout").asInt()).isEqualTo(1800);
    }

    @Test
    @DisplayName("예상하지 못한 처리 오류는 삭제하지 않아 SQS 재전달에 맡긴다")
    void process_unexpectedFailure_keepsMessage() {
        // Given
        given(service.process(any())).willThrow(new IllegalStateException("test"));
        // When
        worker.process(message);
        // Then
        assertThat(operations).isEmpty();
        heartbeatAction.get().run();
        assertThat(operations).isEmpty();
    }

    @Test
    @DisplayName("삭제 요청 실패를 삼켜도 다시 발송할 수 있음을 숨기지 않는다")
    void process_deleteFailure_leavesMessageForRedelivery() {
        // Given
        failDelete = true;
        // When
        worker.process(message);
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        heartbeatAction.get().run();
        assertThat(operations).hasSize(1);
        assertThat(logsAtStage("worker_message_deleted")).isEmpty();
        assertThat(logsAtStage("worker_sqs_delete_failed")).singleElement()
                .satisfies(log -> assertThat((long) log.get("sqsDeleteDurationMs"))
                        .isNotNegative());
        assertThat(logsAtStage("worker_attempt_completed")).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("outcome", "FAILED"));
        assertThat(appender.list.stream()
                .filter(event -> event.getLevel() == ch.qos.logback.classic.Level.ERROR).toList())
                .hasSize(1);
    }

    @Test
    @DisplayName("해석할 수 없는 JSON은 발송하지 않고 영구 오류로 완료한다")
    void process_invalidJson_completesWithoutSending() {
        // When
        worker.process(message.toBuilder().body("invalid").build());
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        verifyNoInteractions(service);
    }
}
