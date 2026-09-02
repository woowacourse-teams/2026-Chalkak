package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.notification.domain.NotificationChannel;
import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import com.chalkak.backend.notification.service.NotificationDispatcher;
import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationOutboxClaimService;
import com.chalkak.backend.notification.service.NotificationOutboxStatusService;
import com.chalkak.backend.notification.service.NotificationOutboxWorker;
import com.chalkak.backend.notification.service.NotificationRetryPolicy;
import com.chalkak.backend.notification.service.NotificationSender;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class NotificationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    NotificationConfiguration.class,
                    NotificationDispatcher.class
            )
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(
                    NotificationOutboxRepository.class,
                    () -> mock(NotificationOutboxRepository.class)
            )
            .withPropertyValues(
                    "chalkak.admin.notification.delivery-enabled=true",
                    "chalkak.admin.notification.admin-web-base-url=https://admin.example.com",
                    "chalkak.admin.notification.poll-interval=10s",
                    "chalkak.admin.notification.processing-lease=30s",
                    "chalkak.admin.notification.batch-size=20",
                    "chalkak.admin.notification.max-attempts=5",
                    "chalkak.admin.notification.initial-retry-delay=30s",
                    "chalkak.admin.notification.max-retry-delay=10m",
                    "chalkak.admin.notification.slack.webhook-url="
                            + "https://hooks.slack.com/services/TEST/TEST/TEST",
                    "chalkak.admin.notification.slack.connect-timeout=2s",
                    "chalkak.admin.notification.slack.read-timeout=3s"
            );

    @Test
    @DisplayName("알림 설정은 redirect를 따르지 않는 JDK HTTP 클라이언트와 Slack 발송기를 등록한다")
    void create_validProperties_registersSafeSlackClientAndSender() {
        // When & Then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(NotificationProperties.class);
            assertThat(context).hasBean("slackNotificationHttpClient");
            assertThat(context).hasBean("slackNotificationRestClient");
            assertThat(context).hasSingleBean(NotificationMessageFactory.class);
            assertThat(context).hasSingleBean(NotificationSender.class);
            assertThat(context).hasSingleBean(NotificationOutboxClaimService.class);
            assertThat(context).hasSingleBean(NotificationOutboxStatusService.class);
            assertThat(context).hasSingleBean(NotificationRetryPolicy.class);
            assertThat(context).hasSingleBean(NotificationOutboxWorker.class);

            HttpClient httpClient = context.getBean(
                    "slackNotificationHttpClient",
                    HttpClient.class
            );
            assertThat(httpClient.followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
            assertThat(httpClient.connectTimeout()).contains(Duration.ofSeconds(2));
            assertThat(context.getBean("slackNotificationRestClient"))
                    .isInstanceOf(RestClient.class);
            assertThat(context.getBean(NotificationSender.class).supportedChannel())
                    .isEqualTo(NotificationChannel.SLACK);
        });
    }
}
