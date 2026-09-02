package com.chalkak.backend.notification.infrastructure.infra;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {

    @Bean("slackNotificationHttpClient")
    public HttpClient slackNotificationHttpClient(NotificationProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.slack().connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("slackNotificationRestClient")
    public RestClient slackNotificationRestClient(
            @Qualifier("slackNotificationHttpClient") HttpClient httpClient,
            NotificationProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.slack().readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public NotificationMessageFactory notificationMessageFactory(
            NotificationProperties properties
    ) {
        return new NotificationMessageFactory(properties.adminWebBaseUrl());
    }

    @Bean
    public NotificationSender slackNotificationSender(
            @Qualifier("slackNotificationRestClient") RestClient restClient,
            NotificationProperties properties
    ) {
        return new SlackIncomingWebhookNotificationSender(
                restClient,
                properties.slack().webhookUrl()
        );
    }

    @Bean
    public NotificationOutboxClaimService notificationOutboxClaimService(
            NotificationOutboxRepository notificationOutboxRepository,
            Clock clock,
            NotificationProperties properties
    ) {
        return new NotificationOutboxClaimService(
                notificationOutboxRepository,
                clock,
                properties.processingLease()
        );
    }

    @Bean
    public NotificationOutboxStatusService notificationOutboxStatusService(
            NotificationOutboxRepository notificationOutboxRepository,
            Clock clock
    ) {
        return new NotificationOutboxStatusService(notificationOutboxRepository, clock);
    }

    @Bean
    public NotificationRetryPolicy notificationRetryPolicy(
            NotificationProperties properties
    ) {
        return new NotificationRetryPolicy(
                properties.maxAttempts(),
                properties.initialRetryDelay(),
                properties.maxRetryDelay()
        );
    }

    @Bean
    public NotificationOutboxWorker notificationOutboxWorker(
            NotificationOutboxClaimService claimService,
            NotificationOutboxStatusService statusService,
            NotificationMessageFactory messageFactory,
            NotificationDispatcher dispatcher,
            NotificationRetryPolicy retryPolicy,
            Clock clock,
            NotificationProperties properties
    ) {
        return new NotificationOutboxWorker(
                claimService,
                statusService,
                messageFactory,
                dispatcher,
                retryPolicy,
                clock,
                properties.batchSize()
        );
    }
}
