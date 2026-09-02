package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationMessageFactory;
import com.chalkak.backend.notification.service.NotificationSender;
import com.chalkak.backend.notification.service.PostModerationPendingNotificationListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationProperties.class)
@ConditionalOnProperty(
        prefix = "chalkak.admin.notification",
        name = "delivery-enabled",
        havingValue = "true"
)
public class NotificationConfiguration {

    @Bean
    public NotificationMessageFactory notificationMessageFactory(
            NotificationProperties properties
    ) {
        return new NotificationMessageFactory(properties.adminWebBaseUrl());
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
