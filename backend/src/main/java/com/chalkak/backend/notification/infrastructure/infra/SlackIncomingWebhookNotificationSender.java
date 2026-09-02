package com.chalkak.backend.notification.infrastructure.infra;

import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.service.NotificationMessage;
import com.chalkak.backend.notification.service.NotificationSendResult;
import com.chalkak.backend.notification.service.NotificationSender;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공급자 중립 알림 메시지를 Slack Incoming Webhook Block Kit 요청으로 변환한다.
 */
public final class SlackIncomingWebhookNotificationSender implements NotificationSender {

    private static final String RATE_LIMITED_FAILURE_CODE = "SLACK_RATE_LIMITED";
    private static final String CLIENT_ERROR_FAILURE_CODE = "SLACK_CLIENT_ERROR";
    private static final String SERVER_ERROR_FAILURE_CODE = "SLACK_SERVER_ERROR";
    private static final String TIMEOUT_FAILURE_CODE = "SLACK_TIMEOUT";
    private static final String NETWORK_ERROR_FAILURE_CODE = "SLACK_NETWORK_ERROR";
    private static final String UNEXPECTED_RESPONSE_FAILURE_CODE = "SLACK_UNEXPECTED_RESPONSE";

    private final RestClient restClient;
    private final URI webhookUri;

    public SlackIncomingWebhookNotificationSender(RestClient restClient, URI webhookUri) {
        this.restClient = Objects.requireNonNull(restClient);
        this.webhookUri = Objects.requireNonNull(webhookUri);
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.SLACK;
    }

    @Override
    public NotificationSendResult send(NotificationMessage message) {
        SlackPayload payload = createPayload(message);

        try {
            return restClient.post()
                    .uri(webhookUri)
                    .contentType(APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) -> toResult(
                            response.getStatusCode(),
                            response.getHeaders()
                    ));
        } catch (RestClientException exception) {
            if (hasTimeoutCause(exception)) {
                return NotificationSendResult.retryable(null, TIMEOUT_FAILURE_CODE);
            }
            return NotificationSendResult.retryable(null, NETWORK_ERROR_FAILURE_CODE);
        }
    }

    private SlackPayload createPayload(NotificationMessage message) {
        SlackText title = new SlackText("plain_text", message.title());
        SlackText body = new SlackText("plain_text", message.body());
        SlackText occurredAt = new SlackText(
                "plain_text",
                "발생 시각: " + message.occurredAt()
        );
        SlackText action = new SlackText(
                "mrkdwn",
                "<" + message.actionUri() + "|" + message.actionLabel() + ">"
        );

        return new SlackPayload(
                message.title(),
                List.of(
                        new SlackTextBlock("header", title),
                        new SlackTextBlock("section", body),
                        new SlackContextBlock("context", List.of(occurredAt)),
                        new SlackTextBlock("section", action)
                )
        );
    }

    private NotificationSendResult toResult(HttpStatusCode statusCode, HttpHeaders headers) {
        if (statusCode.is2xxSuccessful()) {
            return NotificationSendResult.sent();
        }
        if (statusCode.value() == 429) {
            return NotificationSendResult.retryable(
                    parseRetryAfter(headers),
                    RATE_LIMITED_FAILURE_CODE
            );
        }
        if (statusCode.is4xxClientError()) {
            return NotificationSendResult.permanentFailure(CLIENT_ERROR_FAILURE_CODE);
        }
        if (statusCode.is5xxServerError()) {
            return NotificationSendResult.retryable(null, SERVER_ERROR_FAILURE_CODE);
        }
        return NotificationSendResult.permanentFailure(UNEXPECTED_RESPONSE_FAILURE_CODE);
    }

    private Duration parseRetryAfter(HttpHeaders headers) {
        String retryAfter = headers.getFirst(RETRY_AFTER);
        if (retryAfter == null) {
            return null;
        }

        try {
            long retryAfterSeconds = Long.parseLong(retryAfter);
            if (retryAfterSeconds < 0) {
                return null;
            }
            return Duration.ofSeconds(retryAfterSeconds);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean hasTimeoutCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof HttpTimeoutException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private record SlackPayload(String text, List<Object> blocks) {
    }

    private record SlackTextBlock(String type, SlackText text) {
    }

    private record SlackContextBlock(String type, List<SlackText> elements) {
    }

    private record SlackText(String type, String text) {
    }
}
