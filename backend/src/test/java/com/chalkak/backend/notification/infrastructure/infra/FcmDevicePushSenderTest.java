package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.service.DevicePushRequest;
import com.chalkak.backend.notification.service.DevicePushResult;
import com.chalkak.backend.notification.service.DevicePushResult.Status;
import com.chalkak.backend.notification.service.PushMessage;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.sun.net.httpserver.HttpServer;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class FcmDevicePushSenderTest {
    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<JsonNode> bodies = new ArrayList<>();
    private HttpServer server;
    private FirebaseApp app;
    private FcmDevicePushSender sender;
    private volatile Instant now;
    private int responseStatus;
    private String errorStatus;
    private String detailCode;
    private String retryAfter;
    private boolean acceptSecond;
    private Instant nowAfterFirst;
    private DevicePushRequest request;

    @BeforeEach
    void setUp() throws Exception {
        now = NOW;
        responseStatus = 200;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int attempt = calls.incrementAndGet();
            bodies.add(mapper.readTree(exchange.getRequestBody().readAllBytes()));
            int status = responseStatus;
            if (acceptSecond && attempt > 1)
                status = 200;
            String body = "{\"name\":\"projects/test/messages/accepted\"}";
            if (status != 200) {
                body = "{\"error\":{\"code\":" + status + ",\"status\":\"" + errorStatus
                        + "\",\"message\":\"failure\",\"details\":[{\"@type\":\"type.googleapis.com/google.firebase.fcm.v1.FcmError\",\"errorCode\":\""
                        + detailCode + "\"}]}}";
            }
            if (retryAfter != null)
                exchange.getResponseHeaders().set("Retry-After", retryAfter);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
            if (nowAfterFirst != null && attempt == 1)
                now = nowAfterFirst;
        });
        server.start();
        Clock clock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }
            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }
            @Override
            public Instant instant() {
                return now;
            }
        };
        // 실제 Firebase 대신 로컬 HTTP 서버로 연결하며 전송은 Google 기본 구현을 사용한다.
        NetHttpTransport transport = new NetHttpTransport.Builder()
                .setConnectionFactory(url -> (HttpURLConnection) URI.create(url.toString().replace(
                        "https://fcm.googleapis.com",
                        "http://127.0.0.1:" + server.getAddress().getPort())).toURL()
                        .openConnection())
                .build();
        app = FirebaseApp.initializeApp(FirebaseOptions.builder().setProjectId("test")
                .setCredentials(new GoogleCredentials(new AccessToken("test-access-token",
                        new Date(System.currentTimeMillis() + 3600000))) {
                    @Override
                    public AccessToken refreshAccessToken() {
                        return new AccessToken("test-refreshed-token",
                                new Date(System.currentTimeMillis() + 3600000));
                    }
                })
                .setHttpTransport(transport).build(), UUID.randomUUID().toString());
        sender = new FcmDevicePushSender(FirebaseMessaging.getInstance(app), clock);
        PushMessage message = new PushMessage(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NotificationType.POST_APPROVED, NotificationSourceType.POST,
                UUID.randomUUID(), NOW, NOW.plusSeconds(1800));
        request = new DevicePushRequest(message, "fcm-test-token", "승인되었습니다", "사진을 확인해주세요",
                message.expiresAt());
    }

    @AfterEach
    void tearDown() {
        app.delete();
        server.stop(0);
    }

    @Test
    @DisplayName("실제 SDK 요청은 화면 이동 정보·Android TTL·APNs 절대 만료를 포함한다")
    void send_accepted_sendsPlatformExpirationAndNavigation() {
        // When & Then
        assertThat(sender.send(request).status()).isEqualTo(Status.ACCEPTED);
        JsonNode body = bodies.getFirst().get("message");
        assertThat(body.get("android").get("ttl").asText()).isEqualTo("1800s");
        assertThat(body.get("apns").get("headers").get("apns-expiration").asText())
                .isEqualTo(Long.toString(NOW.plusSeconds(1800).getEpochSecond()));
        assertThat(body.get("data").get("notificationId").asText())
                .isEqualTo(request.message().notificationId().toString());
        assertThat(body.get("data").has("userId")).isFalse();
        assertThat(body.get("data").has("rejectionReason")).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND,UNREGISTERED,INVALID_TOKEN",
            "400,INVALID_ARGUMENT,INVALID_ARGUMENT,PERMANENT_FAILURE",
            "403,PERMISSION_DENIED,SENDER_ID_MISMATCH,PERMANENT_FAILURE",
            "401,UNAUTHENTICATED,THIRD_PARTY_AUTH_ERROR,PERMANENT_FAILURE",
            "429,RESOURCE_EXHAUSTED,QUOTA_EXCEEDED,RETRYABLE", "500,INTERNAL,INTERNAL,RETRYABLE"})
    @DisplayName("FCM 응답을 무효 토큰·영구 오류·일시 실패로 분류한다")
    void send_fcmError_classifiesResult(int code, String status, String detail, Status expected) {
        // Given
        responseStatus = code;
        errorStatus = status;
        detailCode = detail;
        // When & Then
        assertThat(sender.send(request).status()).isEqualTo(expected);
        assertThat(calls.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("SDK 재시도에도 최초 계산한 Android TTL과 iOS 절대 만료를 유지한다")
    void send_unavailableThenAccepted_preservesInitialExpiration() {
        // Given
        responseStatus = 503;
        errorStatus = "UNAVAILABLE";
        detailCode = "UNAVAILABLE";
        acceptSecond = true;
        nowAfterFirst = NOW.plusSeconds(10);
        // When & Then
        assertThat(sender.send(request).status()).isEqualTo(Status.ACCEPTED);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(bodies.getLast().get("message").get("android").get("ttl").asText())
                .isEqualTo("1800s");
        assertThat(bodies.getLast().get("message").get("apns").get("headers")
                .get("apns-expiration").asText())
                .isEqualTo(Long.toString(NOW.plusSeconds(1800).getEpochSecond()));
    }

    @Test
    @DisplayName("긴 Retry-After는 SQS 재시도 지연으로 전달한다")
    void send_longRetryAfter_preservesDelay() {
        // Given
        responseStatus = 429;
        errorStatus = "RESOURCE_EXHAUSTED";
        detailCode = "QUOTA_EXCEEDED";
        retryAfter = "120";
        // When & Then
        assertThat(sender.send(request).retryAfter()).isEqualTo(java.time.Duration.ofSeconds(120));
    }

    @ParameterizedTest
    @CsvSource({"'Wed, 07 Oct 2026 00:02:00 GMT',120", "invalid-header,60", "-1,60",
            "59,60", "60,60", "61,61"})
    @DisplayName("HTTP 날짜 Retry-After는 해석하고 잘못된 값은 최소 간격을 유지한다")
    void send_retryAfterHeader_parsesDateOrUsesMinimum(String header, long seconds) {
        // Given
        responseStatus = 429;
        errorStatus = "RESOURCE_EXHAUSTED";
        detailCode = "QUOTA_EXCEEDED";
        retryAfter = header;
        // When & Then
        assertThat(sender.send(request).retryAfter())
                .isEqualTo(java.time.Duration.ofSeconds(seconds));
    }

    @ParameterizedTest
    @ValueSource(longs = {1799, 1800, 1801})
    @DisplayName("FCM 요청 직전 기한을 확인하여 30분 직전만 발송하고 정각·이후에는 생략한다")
    void send_deadlineBoundary_acceptsBeforeAndSkipsAtDeadline(long seconds) {
        // Given
        now = NOW.plusSeconds(seconds);
        // When
        DevicePushResult result = sender.send(request);
        // Then
        if (seconds < 1800) {
            assertThat(result.status()).isEqualTo(Status.ACCEPTED);
            assertThat(calls.get()).isEqualTo(1);
            return;
        }
        assertThat(result.status()).isEqualTo(Status.SKIPPED);
        assertThat(calls.get()).isZero();
    }

    @Test
    @DisplayName("기한 직전 시작한 SDK 재시도는 기한이 지나도 수락 결과로 완료한다")
    void send_deadlineDuringSdkRetry_finishesStartedSend() {
        // Given
        responseStatus = 503;
        errorStatus = "UNAVAILABLE";
        detailCode = "UNAVAILABLE";
        now = NOW.plusSeconds(1799);
        nowAfterFirst = NOW.plusSeconds(1800);
        acceptSecond = true;
        // When & Then
        assertThat(sender.send(request).status()).isEqualTo(Status.ACCEPTED);
        assertThat(calls.get()).isEqualTo(2);
        assertThat(bodies.getLast().get("message").get("android").get("ttl").asText())
                .isEqualTo("1s");
    }
}
