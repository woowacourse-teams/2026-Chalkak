package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.DevicePushRequest;
import com.chalkak.backend.notification.service.DevicePushResult;
import com.chalkak.backend.notification.service.DevicePushSender;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

public class FcmDevicePushSender implements DevicePushSender {
    private static final Duration MINIMUM_RETRY_DELAY = Duration.ofSeconds(60);

    private final FirebaseMessaging messaging;
    private final FcmHttpTransport transport;
    private final Clock clock;

    public FcmDevicePushSender(
            FirebaseMessaging messaging,
            FcmHttpTransport transport,
            Clock clock
    ) {
        this.messaging = messaging;
        this.transport = transport;
        this.clock = clock;
    }

    @Override
    public DevicePushResult send(DevicePushRequest request) {
        if (!request.isSendableAt(clock.instant())) {
            return DevicePushResult.skipped("EXPIRED");
        }
        transport.setDeadline(request.expiresAt());
        try {
            messaging.send(createMessage(request));
            return DevicePushResult.accepted();
        } catch (FirebaseMessagingException exception) {
            return toResult(exception);
        } finally {
            transport.clearDeadline();
        }
    }

    private Message createMessage(DevicePushRequest request) {
        Message.Builder builder = Message.builder();
        builder.setToken(request.token());
        builder.setNotification(createNotification(request));
        addNavigationData(builder, request);
        builder.setAndroidConfig(createAndroidConfig(request));
        builder.setApnsConfig(createApnsConfig(request));
        return builder.build();
    }

    private Notification createNotification(DevicePushRequest request) {
        return Notification.builder().setTitle(request.title()).setBody(request.body()).build();
    }

    private void addNavigationData(Message.Builder builder, DevicePushRequest request) {
        var source = request.message();
        builder.putData("eventId", source.eventId().toString());
        builder.putData("notificationId", source.notificationId().toString());
        builder.putData("type", source.type().name());
        builder.putData("sourceType", source.sourceType().name());
        builder.putData("sourceId", source.sourceId().toString());
    }

    private AndroidConfig createAndroidConfig(DevicePushRequest request) {
        Duration remaining = Duration.between(clock.instant(), request.expiresAt());
        return AndroidConfig.builder().setTtl(remaining.toMillis()).build();
    }

    private ApnsConfig createApnsConfig(DevicePushRequest request) {
        String expiration = Long.toString(request.expiresAt().getEpochSecond());
        Aps payload = Aps.builder().setSound("default").build();
        return ApnsConfig.builder().putHeader("apns-expiration", expiration)
                .putHeader("apns-push-type", "alert").setAps(payload).build();
    }

    private DevicePushResult toResult(FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        String errorCode = findErrorCode(exception);
        if (code == MessagingErrorCode.UNREGISTERED) {
            return DevicePushResult.invalidToken(errorCode);
        }
        if (isRetryable(exception)) {
            return DevicePushResult.retryable(errorCode, findRetryAfter(exception));
        }
        // INVALID_ARGUMENT은 메시지 형식 오류도 포함하며, 프로젝트·APNs 인증 오류도 토큰 무효를 뜻하지 않는다.
        // 정상 기기를 잘못 삭제하지 않도록 UNREGISTERED만 삭제 대상으로 분류한다.
        return DevicePushResult.permanentFailure(errorCode);
    }

    private String findErrorCode(FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        if (code != null) {
            return code.name();
        }
        return exception.getErrorCode().name();
    }

    private boolean isRetryable(FirebaseMessagingException exception) {
        MessagingErrorCode code = exception.getMessagingErrorCode();
        return code == MessagingErrorCode.UNAVAILABLE || code == MessagingErrorCode.INTERNAL
                || code == MessagingErrorCode.QUOTA_EXCEEDED
                || exception.getErrorCode() == ErrorCode.UNAVAILABLE
                || exception.getErrorCode() == ErrorCode.INTERNAL
                || exception.getErrorCode() == ErrorCode.DEADLINE_EXCEEDED
                || exception.getErrorCode() == ErrorCode.UNKNOWN;
    }

    private Duration findRetryAfter(FirebaseMessagingException exception) {
        if (exception.getHttpResponse() == null) {
            return MINIMUM_RETRY_DELAY;
        }
        String header = findRetryAfterHeader(exception.getHttpResponse().getHeaders());
        return parseRetryAfter(header);
    }

    private Duration parseRetryAfter(String header) {
        if (header == null) {
            return MINIMUM_RETRY_DELAY;
        }
        try {
            Duration delay = Duration.ofSeconds(Long.parseLong(header));
            return normalizeRetryAfter(delay);
        } catch (NumberFormatException exception) {
            return parseRetryAfterDate(header);
        }
    }

    private Duration parseRetryAfterDate(String header) {
        try {
            ZonedDateTime retryAt = ZonedDateTime.parse(header,
                    DateTimeFormatter.RFC_1123_DATE_TIME);
            Duration delay = Duration.between(clock.instant(), retryAt.toInstant());
            return normalizeRetryAfter(delay);
        } catch (DateTimeParseException exception) {
            return MINIMUM_RETRY_DELAY;
        }
    }

    private Duration normalizeRetryAfter(Duration delay) {
        if (delay.compareTo(MINIMUM_RETRY_DELAY) > 0) {
            return delay;
        }
        return MINIMUM_RETRY_DELAY;
    }

    private String findRetryAfterHeader(Map<String, Object> headers) {
        String value = null;
        for (var header : headers.entrySet()) {
            value = selectHeaderValue(header, value);
        }
        return value;
    }

    private String selectHeaderValue(Map.Entry<String, Object> header, String previous) {
        if (!header.getKey().equalsIgnoreCase("Retry-After")) {
            return previous;
        }
        return toHeaderValue(header.getValue());
    }

    private String toHeaderValue(Object value) {
        if (value instanceof List<?> values && !values.isEmpty()) {
            return String.valueOf(values.getFirst());
        }
        return String.valueOf(value);
    }
}
