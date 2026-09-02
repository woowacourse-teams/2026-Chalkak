package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationOutboxWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        prefix = "chalkak.admin.notification",
        name = "delivery-enabled",
        havingValue = "true"
)
public class NotificationSchedulingConfiguration {

    @Bean
    public NotificationOutboxScheduler notificationOutboxScheduler(
            NotificationOutboxWorker notificationOutboxWorker
    ) {
        return new NotificationOutboxScheduler(notificationOutboxWorker);
    }
}
