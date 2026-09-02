package com.chalkak.backend.user.infrastructure.infra;

import com.chalkak.backend.user.domain.SignatureImagePolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(ImageProperties.class)
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(ImageProperties imageProperties) {
        return S3Client.builder()
                .region(Region.of(imageProperties.region()))
                .credentialsProvider(credentialsProvider(imageProperties))
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(ImageProperties imageProperties) {
        return S3Presigner.builder()
                .region(Region.of(imageProperties.region()))
                .credentialsProvider(credentialsProvider(imageProperties))
                .build();
    }

    /**
     * 배포 환경은 EC2 인스턴스 역할을 쓴다. 개인 자격증명을 발급받을 수 없는 로컬에서만 공개 읽기 권한에 기대어
     * 익명 호출로 전환한다. 기본값이 {@code false}라 설정하지 않은 환경은 영향이 없다.
     */
    private AwsCredentialsProvider credentialsProvider(ImageProperties imageProperties) {
        if (imageProperties.anonymousAccess()) {
            return AnonymousCredentialsProvider.create();
        }
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    public SignatureImagePolicy signatureImagePolicy(ImageProperties imageProperties) {
        ImageProperties.Signature signature = imageProperties.signature();

        return new SignatureImagePolicy(signature.maxBytes(), signature.allowedContentTypes());
    }
}
