package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.PushMessagePublisher;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqsPushProperties.class)
public class SqsPushConfiguration {

    // SQS 발행 대기는 Relay 전용 스레드에서 실행하고 공용 예약 작업은 유지한다.
    @Bean(defaultCandidate = false)
    @ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "relay-enabled", havingValue = "true")
    public ThreadPoolTaskScheduler notificationRelayTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("notification-relay-");
        return scheduler;
    }

    @Bean
    public SqsClient notificationSqsClient(SqsPushProperties properties) {
        return SqsClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(configuration -> configuration
                        .apiCallTimeout(Duration.ofSeconds(5))
                        .apiCallAttemptTimeout(Duration.ofSeconds(5))
                        .retryStrategy(StandardRetryStrategy.builder().maxAttempts(1).build()))
                .build();
    }

    @Bean
    public PushMessagePublisher pushMessagePublisher(
            @Qualifier("notificationSqsClient") SqsClient notificationSqsClient,
            SqsPushProperties properties,
            ObjectMapper objectMapper
    ) {
        return new SqsPushMessagePublisher(notificationSqsClient, properties.queueUrl(),
                objectMapper);
    }
}
