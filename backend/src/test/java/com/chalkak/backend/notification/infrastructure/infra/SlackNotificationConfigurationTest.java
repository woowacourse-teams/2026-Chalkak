package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.service.NotificationSender;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class SlackNotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SlackNotificationConfiguration.class)
            .withPropertyValues(
                    "chalkak.admin.notification.slack.webhook-url="
                            + "https://hooks.slack.com/services/TEST/TEST/TEST",
                    "chalkak.admin.notification.slack.connect-timeout=2s",
                    "chalkak.admin.notification.slack.read-timeout=3s"
            );

    @Test
    @DisplayName("Slack 설정은 redirect를 따르지 않는 HTTP 클라이언트와 발송기를 등록한다")
    void create_deliveryEnabled_registersSafeSlackClientAndSender() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.admin.notification.delivery-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(SlackNotificationProperties.class);
                    assertThat(context).hasBean("slackNotificationHttpClient");
                    assertThat(context).hasBean("slackNotificationRestClient");
                    assertThat(context).hasSingleBean(NotificationSender.class);

                    HttpClient httpClient = context.getBean(
                            "slackNotificationHttpClient",
                            HttpClient.class
                    );
                    assertThat(httpClient.followRedirects())
                            .isEqualTo(HttpClient.Redirect.NEVER);
                    assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(2));
                    assertThat(context.getBean("slackNotificationRestClient"))
                            .isInstanceOf(RestClient.class);
                });
    }

    @Test
    @DisplayName("알림 발송을 끄면 Slack 관련 Bean을 등록하지 않는다")
    void create_deliveryDisabled_doesNotRegisterSlackBeans() {
        // When & Then
        contextRunner
                .withPropertyValues(
                        "chalkak.admin.notification.delivery-enabled=false",
                        "chalkak.admin.notification.slack.webhook-url="
                                + "http://localhost/services/TEST/TEST/TEST"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(SlackNotificationProperties.class);
                    assertThat(context).doesNotHaveBean(NotificationSender.class);
                    assertThat(context).doesNotHaveBean("slackNotificationHttpClient");
                    assertThat(context).doesNotHaveBean("slackNotificationRestClient");
                });
    }
}
