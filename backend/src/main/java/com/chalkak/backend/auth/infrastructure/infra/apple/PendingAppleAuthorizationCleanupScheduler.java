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

    /**
     * 만료 목록을 뽑은 뒤 폐기까지 시간이 걸리므로, 그 사이에 만료 연장이 커밋된 행도 폐기될 수
     * 있다. 연장이 만료 시각을 걸치는 수 ms에 이 조회가 겹쳐야 하고, 걸려도 사용자가 다시
     * 로그인하면 복구되며 토큰은 정상 폐기되므로 감수한다. 주기를 크게 줄이면 다시 검토한다.
     */
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
                    "만료된 임시 Apple 인증 정보 폐기에 실패했습니다. id={}",
                    authorization.getId(),
                    exception);
        }
    }
}
