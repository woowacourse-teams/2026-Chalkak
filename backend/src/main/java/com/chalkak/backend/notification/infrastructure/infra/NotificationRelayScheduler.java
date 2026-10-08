package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "relay-enabled", havingValue = "true")
public class NotificationRelayScheduler {

    private final NotificationRelayService notificationRelayService;

    // 정상 발행은 1초마다 확인한다. 실패 작업의 1분 대기는 DB next_attempt_at으로 관리한다.
    @Scheduled(fixedDelay = 1000, scheduler = "notificationRelayTaskScheduler")
    public void publishPendingNotifications() {
        notificationRelayService.publishPendingNotifications();
    }
}
