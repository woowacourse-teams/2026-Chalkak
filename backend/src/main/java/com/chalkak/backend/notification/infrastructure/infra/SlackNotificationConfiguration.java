package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationSender;
import java.net.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SlackNotificationProperties.class)
@ConditionalOnProperty(
        prefix = "chalkak.admin.notification",
        name = "delivery-enabled",
        havingValue = "true"
)
public class SlackNotificationConfiguration {

    @Bean("slackNotificationHttpClient")
    public HttpClient slackNotificationHttpClient(SlackNotificationProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("slackNotificationRestClient")
    public RestClient slackNotificationRestClient(
            @Qualifier("slackNotificationHttpClient") HttpClient httpClient,
            SlackNotificationProperties properties
    ) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    public NotificationSender slackNotificationSender(
            @Qualifier("slackNotificationRestClient") RestClient restClient,
            SlackNotificationProperties properties
    ) {
        return new SlackIncomingWebhookNotificationSender(
                restClient,
                properties.webhookUrl()
        );
    }
}
