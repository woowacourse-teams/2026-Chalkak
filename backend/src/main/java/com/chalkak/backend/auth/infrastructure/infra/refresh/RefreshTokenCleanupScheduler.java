package com.chalkak.backend.auth.infrastructure.infra.refresh;

import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 더는 쓰일 일이 없는 리프레시 토큰을 주기적으로 지운다.
 *
 * <p>액세스 토큰이 15분이라 활성 기기 하나가 하루에 백 개 가까운 행을 만들고, 90일 계보가 끝날 때까지
 * 쌓이면 기기마다 수천 행이 된다. 회전 계보는 조회 경로에 그대로 얹히므로 지우지 않으면 재발급이
 * 점점 느려진다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    /**
     * 폐기된 행을 남겨 두는 기간. 탈취로 계보를 끊은 직후에는 어떤 토큰이 언제 회전·폐기됐는지가
     * 사고를 들여다보는 유일한 기록이므로, 폐기하자마자 지우지 않고 일주일은 남긴다.
     */
    private static final Duration REVOKED_RETENTION = Duration.ofDays(7);

    private final UserRefreshTokenRepository userRefreshTokenRepository;
    private final AdminRefreshTokenRepository adminRefreshTokenRepository;
    private final Clock clock;

    /** 서비스가 한가한 새벽에 돌린다. 운영 기준 시각이 KST이므로 시간대를 명시한다. */
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteUnusableTokens() {
        Instant now = clock.instant();
        Instant revokedThreshold = now.minus(REVOKED_RETENTION);
        userRefreshTokenRepository.deleteUnusableBefore(now, revokedThreshold);
        adminRefreshTokenRepository.deleteUnusableBefore(now, revokedThreshold);
    }
}
