package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.PushMessagePublisher;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.StandardRetryStrategy;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SqsPushProperties.class)
public class SqsPushConfiguration {

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
            SqsClient notificationSqsClient,
            SqsPushProperties properties,
            ObjectMapper objectMapper
    ) {
        return new SqsPushMessagePublisher(notificationSqsClient, properties.queueUrl(),
                objectMapper);
    }
}
