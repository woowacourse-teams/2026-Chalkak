package com.chalkak.backend.notification.infrastructure.infra;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.chalkak.backend.notification.service.NotificationMessage;
import com.chalkak.backend.notification.service.NotificationSender;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 공급자 중립 알림 메시지를 Slack Incoming Webhook Block Kit 요청으로 변환한다.
 */
public final class SlackIncomingWebhookNotificationSender implements NotificationSender {

    private final RestClient restClient;
    private final URI webhookUri;

    public SlackIncomingWebhookNotificationSender(RestClient restClient, URI webhookUri) {
        this.restClient = Objects.requireNonNull(restClient);
        this.webhookUri = Objects.requireNonNull(webhookUri);
    }

    @Override
    public boolean send(NotificationMessage message) {
        SlackPayload payload = createPayload(message);

        try {
            return restClient.post()
                    .uri(webhookUri)
                    .contentType(APPLICATION_JSON)
                    .body(payload)
                    .exchange((request, response) ->
                            response.getStatusCode().is2xxSuccessful());
        } catch (RestClientException exception) {
            return false;
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

    private record SlackPayload(String text, List<Object> blocks) {
    }

    private record SlackTextBlock(String type, SlackText text) {
    }

    private record SlackContextBlock(String type, List<SlackText> elements) {
    }

    private record SlackText(String type, String text) {
    }
}
