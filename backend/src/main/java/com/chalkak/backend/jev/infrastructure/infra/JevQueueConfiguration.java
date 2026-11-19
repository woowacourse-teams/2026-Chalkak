package com.chalkak.backend.jev.infrastructure.infra;

import com.chalkak.backend.jev.service.JevJobPublisher;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
public class JevQueueConfiguration {

    @Bean(defaultCandidate = false)
    public ThreadPoolTaskScheduler jevTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // SQS 전송 지연이 이미 받은 결과의 DB 반영을 막지 않도록 두 작업을 분리해 실행한다.
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("jev-dispatch-");
        return scheduler;
    }

    @Bean
    @ConditionalOnProperty(prefix = "chalkak.jev", name = "worker-enabled", havingValue = "true")
    public JevJobPublisher jevJobPublisher(
            @Qualifier("notificationSqsClient") SqsClient client,
            @Value("${chalkak.jev.queue-url}") String queueUrl,
            @Value("${chalkak.image.environment}") String environment,
            ObjectMapper objectMapper
    ) {
        if (queueUrl == null || !queueUrl.matches(
                "https://sqs\\.ap-northeast-2\\.amazonaws\\.com/[0-9]{12}/[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Jev 작업 큐 URL이 필요합니다.");
        }
        return id -> client.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(objectMapper.writeValueAsString(Map.of(
                        "type",
                        "JEV_MODERATION",
                        "environment",
                        environment,
                        "jobId",
                        id
                )))
                .build());
    }
}
