package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushProcessingResult;
import com.chalkak.backend.notification.service.PushWorkerService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
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
    private SqsClient client;
    private SqsPushService worker;
    private PushWorkerService service;
    private ScheduledFuture<?> future;
    private PushMessage push;
    private Message message;
    private boolean failDelete;
    private boolean holdVisibilityResponse;
    private String receiveResponse = "{\"Messages\":[]}";

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            String operation = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            operations.add(operation);
            bodies.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            if (operation.endsWith("ChangeMessageVisibility") && holdVisibilityResponse) {
                visibilityStarted.countDown();
                waitForVisibilityRelease();
            }
            int status = 200;
            String body = "{}";
            if (operation.endsWith("ReceiveMessage"))
                body = receiveResponse;
            if (operation.endsWith("DeleteMessage") && failDelete) {
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
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        future = mock(ScheduledFuture.class);
        org.mockito.Mockito.doAnswer(call -> {
            heartbeatAction.set(call.getArgument(0));
            return future;
        })
                .when(executor).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(),
                        any(TimeUnit.class));
        service = mock(PushWorkerService.class);
        given(service.process(any())).willReturn(PushProcessingResult.completed());
        worker = new SqsPushService(client, endpoint + "/000000000000/test", mapper, service,
                Clock.fixed(NOW, ZoneOffset.UTC), executor);
        push = new PushMessage(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                NotificationType.POST_APPROVED, NotificationSourceType.POST, UUID.randomUUID(), NOW,
                NOW.plusSeconds(1800));
        message = Message.builder().messageId("sqs-id").receiptHandle("receipt-handle")
                .body(mapper.writeValueAsString(push)).build();
    }

    @AfterEach
    void tearDown() {
        allowVisibilityResponse.countDown();
        client.close();
        server.stop(0);
        serverExecutor.shutdownNow();
    }

    private void waitForVisibilityRelease() throws IOException {
        try {
            if (!allowVisibilityResponse.await(5, TimeUnit.SECONDS)) {
                throw new IOException("Test visibility response timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Test visibility response interrupted", exception);
        }
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
    }

    @Test
    @DisplayName("메시지를 한 건 받으면 처리 후 해당 메시지를 삭제한다")
    void poll_oneMessage_processesAndDeletesMessage() throws Exception {
        // Given
        receiveResponse = mapper.writeValueAsString(Map.of("Messages", List.of(Map.of(
                "MessageId", message.messageId(),
                "ReceiptHandle", message.receiptHandle(),
                "MD5OfBody", HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                        .digest(message.body().getBytes(StandardCharsets.UTF_8))),
                "Body", message.body()))));
        // When
        worker.poll();
        // Then
        assertThat(operations).containsExactly("AmazonSQS.ReceiveMessage",
                "AmazonSQS.DeleteMessage");
        assertThat(bodies.getLast().get("ReceiptHandle").asText()).isEqualTo("receipt-handle");
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
        verify(future, org.mockito.Mockito.atLeastOnce()).cancel(false);
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
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),
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
    }

    @Test
    @DisplayName("해석할 수 없는 JSON은 발송하지 않고 영구 오류로 완료한다")
    void process_invalidJson_completesWithoutSending() {
        // When
        worker.process(message.toBuilder().body("invalid").build());
        // Then
        assertThat(operations).containsExactly("AmazonSQS.DeleteMessage");
        org.mockito.Mockito.verifyNoInteractions(service);
    }
}
