package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.TopicPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "topic-push-enabled", havingValue = "true")
public class TopicPushScheduler {

    private final TopicPushService topicPushService;

    @Scheduled(fixedDelay = 1000, scheduler = "notificationTopicTaskScheduler")
    public void publishDueTopics() {
        try {
            topicPushService.publishDueTopics();
        } catch (RuntimeException exception) {
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "topic_scheduler_failed")
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("주제 푸시 설정 조회 실패");
        }
    }
}
