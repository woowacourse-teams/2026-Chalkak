package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.PostLikeMilestoneRelayService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "relay-enabled", havingValue = "true")
public class PostLikeMilestoneRelayScheduler {

    private final PostLikeMilestoneRelayService relayService;

    @Scheduled(fixedDelay = 1000, scheduler = "postLikeMilestoneRelayTaskScheduler")
    public void publishPendingMilestones() {
        relayService.publishPendingMilestones();
    }
}
