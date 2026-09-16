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
     * 만료 목록을 뽑은 뒤 폐기까지 시간이 걸리므로, 그 사이에 만료가 연장된 행도 목록에 남아
     * 폐기된다. 그 틈에 가입까지 끝나면 정식 보관으로 옮긴 토큰이 폐기돼, 탈퇴 시 폐기 요청이
     * 무의미해진다. 재로그인으로 복구되지 않는 경우다.
     *
     * <p>연장 트랜잭션이 만료 시각을 걸치는 수 ms에 이 조회가 겹치고, 다시 그 사이에 사인
     * 업로드·처리·가입 완료가 모두 끝나야 하므로 감수한다. 주기를 크게 줄이거나 보관 기간을
     * 짧게 바꾸면 다시 검토한다.
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
