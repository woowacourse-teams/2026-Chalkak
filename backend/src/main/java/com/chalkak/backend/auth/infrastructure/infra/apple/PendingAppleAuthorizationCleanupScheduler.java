package com.chalkak.backend.auth.infrastructure.infra.apple;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.auth.service.AppleAuthorizationCipher;
import com.chalkak.backend.auth.service.AppleTokenClient;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
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
    private final AppleAuthorizationCipher authorizationCipher;
    private final AppleTokenClient appleTokenClient;
    private final Clock clock;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void revokeAndDeleteExpiredAuthorizations() {
        List<PendingAppleAuthorization> expiredAuthorizations =
                repository.findAllExpiredAtOrBefore(clock.instant());
        expiredAuthorizations.forEach(this::revokeAndDelete);
    }

    private void revokeAndDelete(PendingAppleAuthorization authorization) {
        try {
            String refreshToken = authorizationCipher.decrypt(
                    authorization.getEncryptedRefreshToken());
            appleTokenClient.revokeRefreshToken(refreshToken);
            repository.delete(authorization);
        } catch (RuntimeException exception) {
            log.warn(
                    "만료된 임시 Apple 인증 정보 폐기에 실패했습니다. uploadId={}",
                    authorization.getUploadId(),
                    exception);
        }
    }
}
