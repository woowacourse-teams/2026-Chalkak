package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "chalkak.auth.pending-apple-authorization.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class PendingAppleAuthorizationCleanupScheduler {

    private final PendingAppleAuthorizationRepository repository;
    private final Clock clock;

    @Scheduled(cron = "0 45 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredAuthorizations() {
        repository.deleteAllExpiredAtOrBefore(clock.instant());
    }
}
