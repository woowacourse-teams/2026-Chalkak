package com.chalkak.backend.auth.infrastructure.infra.signup;

import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소진된 회원가입 토큰 기록을 주기적으로 지운다.
 *
 * <p>이 표는 같은 signupToken이 두 번 쓰이는 것을 막으려고 jti를 남기는 것뿐이라, 토큰이 만료된
 * 뒤에는 아무 판정에도 쓰이지 않는다. 만료된 토큰은 서명 검증에서 먼저 걸러지기 때문이다. 가입
 * 한 건마다 한 행씩 늘고 스스로 줄지 않으므로 여기서 정리한다.
 *
 * <p>정리 작업 하나만 끄고 싶을 때를 위한 플래그를 빈에 건다. 스케줄링 기능 자체를 끄는 것은
 * {@code chalkak.scheduling.enabled}이고, 이 키는 다른 주기 작업에 영향을 주지 않는다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "chalkak.auth.consumed-signup-token.cleanup",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ConsumedSignupTokenCleanupScheduler {

    private final ConsumedSignupTokenRepository consumedSignupTokenRepository;
    private final Clock clock;

    /** 서비스가 한가한 새벽에 돌린다. 운영 기준 시각이 KST이므로 시간대를 명시한다. */
    @Scheduled(cron = "0 40 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public void deleteExpiredTokens() {
        consumedSignupTokenRepository.deleteAllExpiredBefore(clock.instant());
    }
}
