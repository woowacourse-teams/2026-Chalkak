package com.chalkak.backend.notification.infrastructure.infra;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class SqsPollingScheduler {
    private static final long POLLING_DELAY_MILLIS = 1;
    private final SqsPushService sqsPushService;

    @Scheduled(fixedDelay = POLLING_DELAY_MILLIS, scheduler = "notificationPollingScheduler")
    public void poll() {
        sqsPushService.poll();
    }
}
