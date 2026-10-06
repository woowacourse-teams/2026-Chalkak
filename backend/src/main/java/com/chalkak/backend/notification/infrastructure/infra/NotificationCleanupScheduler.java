package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.repository.NotificationRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.notification.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationCleanupScheduler {

    private static final Duration NORMAL_RETENTION = Duration.ofDays(30);
    private static final Duration WITHDRAWN_RETENTION = Duration.ofDays(30);

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    @Scheduled(cron = "0 50 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredNotifications() {
        Instant now = clock.instant();
        notificationRepository.deleteExpiredForActiveUsers(now.minus(NORMAL_RETENTION));
        notificationRepository.deleteExpiredForWithdrawnUsers(now.minus(WITHDRAWN_RETENTION));
    }
}
