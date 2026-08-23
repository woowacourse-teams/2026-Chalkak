package com.chalkak.backend.user.infrastructure.infra;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(ImageProperties.class)
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(ImageProperties imageProperties) {
        return S3Client.builder()
                .region(Region.of(imageProperties.region()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}