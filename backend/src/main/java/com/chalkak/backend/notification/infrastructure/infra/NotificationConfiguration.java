package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationSender;
import com.chalkak.backend.notification.service.PostModerationPendingNotificationListener;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnProperty(
        prefix = "chalkak.admin.notification",
        name = "delivery-enabled",
        havingValue = "true"
)
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
    public PostModerationPendingNotificationListener postModerationPendingNotificationListener(
            NotificationMessageFactory messageFactory,
            NotificationSender notificationSender
    ) {
        return new PostModerationPendingNotificationListener(
                messageFactory,
                notificationSender
        );
    }
}
