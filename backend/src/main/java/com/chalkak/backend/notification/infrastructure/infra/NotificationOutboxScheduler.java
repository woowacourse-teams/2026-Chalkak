package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.service.NotificationOutboxWorker;
import org.springframework.scheduling.annotation.Scheduled;

public class NotificationOutboxScheduler {

    private final NotificationOutboxWorker notificationOutboxWorker;

    public NotificationOutboxScheduler(NotificationOutboxWorker notificationOutboxWorker) {
        this.notificationOutboxWorker = notificationOutboxWorker;
    }

    @Scheduled(fixedDelayString = "${chalkak.admin.notification.poll-interval}")
    public void processDueNotifications() {
        notificationOutboxWorker.processDueBatch();
    }
}
