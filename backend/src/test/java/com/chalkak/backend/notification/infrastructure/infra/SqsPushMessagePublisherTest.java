package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.service.PushMessage;
import com.chalkak.backend.notification.service.PushPublicationResult;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SqsPushMessagePublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-06T00:00:00Z");
    private static final PushMessage MESSAGE = new PushMessage(UUID.randomUUID(), UUID.randomUUID(),
            UUID.randomUUID(),
            NotificationType.POST_REJECTED, NotificationSourceType.POST, UUID.randomUUID(),
            OCCURRED_AT, OCCURRED_AT.plusSeconds(1800));

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpServer server;
    private SqsClient client;
    private SqsPushMessagePublisher publisher;
    private String requestBody;
    private String requestTarget;
    private int responseStatus;
    private String responseBody;

    @BeforeEach
    void setUp() throws Exception {
        responseStatus = 200;
        responseBody = "{\"MessageId\":\"sqs-accepted-id\"}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestBody = new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);
            requestTarget = exchange.getRequestHeaders().getFirst("X-Amz-Target");
            String body = responseBody;
            if (responseStatus == 200) {
                String messageBody = objectMapper.readTree(requestBody).get("MessageBody").asText();
                String checksum;
                try {
                    checksum = java.util.HexFormat.of()
                            .formatHex(java.security.MessageDigest.getInstance("MD5")
                                    .digest(messageBody.getBytes(StandardCharsets.UTF_8)));
                } catch (java.security.NoSuchAlgorithmException exception) {
                    throw new IllegalStateException(exception);
                }
                body = "{\"MessageId\":\"sqs-accepted-id\",\"MD5OfMessageBody\":\"" + checksum
                        + "\"}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/x-amz-json-1.0");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
        URI endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        client = SqsClient.builder().endpointOverride(endpoint).region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider
                        .create(AwsBasicCredentials.create("test-key", "test-secret")))
                .overrideConfiguration(configuration -> configuration
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
        publisher = new SqsPushMessagePublisher(client,
                endpoint + "/000000000000/chalkak-test-push", objectMapper);
    }

    @AfterEach
    void tearDown() {
        client.close();
        server.stop(0);
    }

    @Test
    @DisplayName("실제 SDK 요청에는 사건·회원·알림·대상·기한만 담고 수락 ID를 반환한다")
    void publish_acceptedHttpResponse_sendsMinimalJsonAndReturnsMessageId() {
        // When
        PushPublicationResult result = publisher.publish(MESSAGE);

        // Then
        assertThat(result).satisfies(value -> assertThat(value.errorCode()).isNull());
        assertThat(result.isAccepted()).isTrue();
        assertThat(result.messageId()).isEqualTo("sqs-accepted-id");
        assertThat(requestTarget).isEqualTo("AmazonSQS.SendMessage");
        JsonNode envelope = objectMapper.readTree(requestBody);
        JsonNode body = objectMapper.readTree(envelope.get("MessageBody").asText());
        assertThat(body.size()).isEqualTo(8);
        assertThat(body.get("eventId").asText()).isEqualTo(MESSAGE.eventId().toString());
        assertThat(body.get("notificationId").asText())
                .isEqualTo(MESSAGE.notificationId().toString());
        assertThat(body.get("userId").asText()).isEqualTo(MESSAGE.userId().toString());
        assertThat(body.get("type").asText()).isEqualTo("POST_REJECTED");
        assertThat(body.get("sourceType").asText()).isEqualTo("POST");
        assertThat(body.get("sourceId").asText()).isEqualTo(MESSAGE.sourceId().toString());
        assertThat(body.get("occurredAt").asText()).isEqualTo(OCCURRED_AT.toString());
        assertThat(body.get("expiresAt").asText()).isEqualTo(MESSAGE.expiresAt().toString());
        assertThat(body.has("rejectionReason")).isFalse();
        assertThat(body.has("fcmToken")).isFalse();
        assertThat(body.has("originalImageUrl")).isFalse();
        assertThat(requests.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"500, ServiceUnavailable, true", "503, InternalError, true",
            "400, RequestThrottled, true", "429, TooManyRequests, true", "403, AccessDenied, false",
            "400, QueueDoesNotExist, false", "400, InvalidMessageContents, false"})
    @DisplayName("SQS 오류 코드·HTTP 상태를 재시도 여부로 변환하고 원문 오류를 전달하지 않는다")
    void publish_serviceError_classifiesRetryability(int status, String code, boolean retryable) {
        // Given
        responseStatus = status;
        responseBody = "{\"__type\":\"" + code + "\",\"message\":\"SECRET_REQUEST_BODY\"}";

        // When
        PushPublicationResult result = publisher.publish(MESSAGE);

        // Then
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.retryable()).isEqualTo(retryable);
        assertThat(result.errorCode()).isEqualTo(code);
        assertThat(result.toString()).doesNotContain("SECRET_REQUEST_BODY");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("외부 오류 코드 형식이 부적절하면 안전한 고정 코드로 바꾼다")
    void publish_invalidErrorCode_returnsSafeErrorCode() {
        // Given
        responseStatus = 500;
        responseBody = "{\"__type\":\"SECRET PRIVATE BODY\",\"message\":\"SECRET_REQUEST_BODY\"}";

        // When & Then
        assertThat(publisher.publish(MESSAGE).errorCode()).isEqualTo("SQS_SERVICE_ERROR");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("수락 ID가 없으면 성공으로 간주하지 않고 결과 불명으로 재시도한다")
    void publish_missingAcceptanceId_retriesUnknownOutcome(String id) {
        // Given
        SqsClient unknownClient = mock(SqsClient.class);
        given(unknownClient.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .willReturn(SendMessageResponse.builder().messageId(id).build());

        // When
        PushPublicationResult result = new SqsPushMessagePublisher(unknownClient, "test-queue",
                objectMapper).publish(MESSAGE);

        // Then
        assertThat(result.isAccepted()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("SQS_ACCEPTANCE_UNKNOWN");
    }

    @Test
    @DisplayName("연결·타임아웃 오류는 수락 여부 불명으로 재시도한다")
    void publish_clientFailure_retriesWithoutRawException() {
        // Given
        SqsClient failingClient = mock(SqsClient.class);
        given(failingClient.sendMessage(org.mockito.ArgumentMatchers.any(SendMessageRequest.class)))
                .willThrow(SdkClientException.create("SECRET_REQUEST_BODY"));

        // When
        PushPublicationResult result = new SqsPushMessagePublisher(failingClient, "test-queue",
                objectMapper).publish(MESSAGE);

        // Then
        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCode()).isEqualTo("SQS_CLIENT_ERROR");
        assertThat(result.toString()).doesNotContain("SECRET_REQUEST_BODY");
    }

    @Test
    @DisplayName("직렬화 오류는 큐에 보내지 않고 영구 실패로 분류한다")
    void publish_serializationFailure_stopsWithoutExternalRequest() {
        // Given
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        given(failingMapper.writeValueAsString(MESSAGE)).willThrow(mock(JacksonException.class));

        // When
        PushPublicationResult result = new SqsPushMessagePublisher(client, "test-queue",
                failingMapper).publish(MESSAGE);

        // Then
        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SQS_MESSAGE_SERIALIZATION_ERROR");
        assertThat(requests.get()).isZero();
    }
}
