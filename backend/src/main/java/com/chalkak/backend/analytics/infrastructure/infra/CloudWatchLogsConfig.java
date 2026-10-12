package com.chalkak.backend.analytics.infrastructure.infra;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;

@Configuration(proxyBeanMethods = false)
public class CloudWatchLogsConfig {

    /** 리전은 S3와 같은 {@code chalkak.image.region}을 쓴다. 배포 환경은 EC2 인스턴스 역할로 호출한다. */
    @Bean
    public CloudWatchLogsClient cloudWatchLogsClient(
            @Value("${chalkak.image.region}") String region
    ) {
        return CloudWatchLogsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
