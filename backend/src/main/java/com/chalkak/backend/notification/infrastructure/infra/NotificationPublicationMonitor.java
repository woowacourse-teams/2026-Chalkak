package com.chalkak.backend.notification.infrastructure.infra;

import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.repository.PublicationBacklog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "prod"})
@RequiredArgsConstructor
@Slf4j
public class NotificationPublicationMonitor {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    // Relay가 멈추거나 오래 실행되어도 관측하도록 공용 스케줄러에서 별도로 조회한다.
    @Scheduled(fixedRate = 60_000)
    public void logBacklog() {
        try {
            PublicationBacklog backlog = notificationRepository.getPublicationBacklog();
            log.atInfo().addKeyValue("type", "notification")
                    .addKeyValue("stage", "publication_snapshot")
                    .addKeyValue("pendingCount", backlog.pendingCount())
                    .addKeyValue("oldestPendingAgeSeconds",
                            getAgeSeconds(backlog.oldestCreatedAt()))
                    .log("알림 발행 대기 상태");
        } catch (RuntimeException exception) {
            // 조회 실패를 대기 0건으로 기록하면 정상으로 오인할 수 있다.
            log.atError().addKeyValue("type", "notification")
                    .addKeyValue("stage", "publication_snapshot_failed")
                    .addKeyValue("errorCode", exception.getClass().getSimpleName())
                    .log("알림 발행 대기 조회 실패");
        }
    }

    private long getAgeSeconds(Instant createdAt) {
        if (createdAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(createdAt, clock.instant()).toSeconds());
    }
}
