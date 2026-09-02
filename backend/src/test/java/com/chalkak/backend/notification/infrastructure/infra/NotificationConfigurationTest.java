package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationSender;
import com.chalkak.backend.notification.service.PostModerationPendingNotificationListener;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class NotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(NotificationConfiguration.class)
            .withPropertyValues(
                    "chalkak.admin.notification.admin-web-base-url=https://admin.example.com",
                    "chalkak.admin.notification.slack.webhook-url="
                            + "https://hooks.slack.com/services/TEST/TEST/TEST",
                    "chalkak.admin.notification.slack.connect-timeout=2s",
                    "chalkak.admin.notification.slack.read-timeout=3s"
            );

    @Test
    @DisplayName("알림 설정은 redirect를 따르지 않는 JDK HTTP 클라이언트와 Slack 발송기를 등록한다")
    void create_validProperties_registersSafeSlackClientAndSender() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.admin.notification.delivery-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(NotificationProperties.class);
                    assertThat(context).hasBean("slackNotificationHttpClient");
                    assertThat(context).hasBean("slackNotificationRestClient");
                    assertThat(context).hasSingleBean(NotificationMessageFactory.class);
                    assertThat(context).hasSingleBean(NotificationSender.class);
                    assertThat(context)
                            .hasSingleBean(PostModerationPendingNotificationListener.class);

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
    void create_deliveryDisabled_doesNotRegisterNotificationBeans() {
        // When & Then
        contextRunner
                .withPropertyValues("chalkak.admin.notification.delivery-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NotificationProperties.class);
                    assertThat(context).doesNotHaveBean(NotificationSender.class);
                    assertThat(context).doesNotHaveBean(
                            PostModerationPendingNotificationListener.class
                    );
                });
    }
}
