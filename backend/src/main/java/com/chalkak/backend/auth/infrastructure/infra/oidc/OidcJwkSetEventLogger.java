package com.chalkak.backend.auth.infrastructure.infra.oidc;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.nimbusds.jose.jwk.source.OutageTolerantJWKSetSource;
import com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource;
import com.nimbusds.jose.jwk.source.RetryingJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.events.Event;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 공개키 목록 조회가 정상 흐름을 벗어난 순간을 제공자 이름과 함께 경고 로그로 남긴다. 검증 실패는 Verifier에서 401로만 바뀌고
 * 원인이 남지 않으므로, 재시도·이전 목록 사용·재조회 제한 중 무엇 때문인지 운영 중에 구분할 수 있게 한다.
 *
 * <p>
 * 재시도와 이전 목록 사용은 재조회 제한 아래에서만 일어나 횟수가 제한되지만, 재조회 제한 알림은 넘친 요청마다 온다. 임의 kid 토큰을
 * 반복해 보내는 것만으로 로그가 쌓이지 않도록 이 알림은 정해진 구간마다 한 번만 남긴다.
 */
@Slf4j
final class OidcJwkSetEventLogger {

    private final SocialProvider provider;
    private final long rateLimitedLogIntervalMillis;
    private final Clock clock;
    private final AtomicLong nextRateLimitedLogAtMillis = new AtomicLong(Long.MIN_VALUE);

    OidcJwkSetEventLogger(
            SocialProvider provider,
            Duration rateLimitedLogInterval,
            Clock clock
    ) {
        this.provider = provider;
        this.rateLimitedLogIntervalMillis = rateLimitedLogInterval.toMillis();
        this.clock = clock;
    }

    <C extends SecurityContext> void logRetrial(Event<RetryingJWKSetSource<C>, C> event) {
        if (!(event instanceof RetryingJWKSetSource.RetrialEvent<C> retrialEvent)) {
            return;
        }
        log.warn(
                "{} 공개키 목록 조회에 실패해 한 번 더 시도합니다. cause={}",
                provider.name(),
                retrialEvent.getException().getMessage());
    }

    <C extends SecurityContext> void logOutage(Event<OutageTolerantJWKSetSource<C>, C> event) {
        if (!(event instanceof OutageTolerantJWKSetSource.OutageEvent<C> outageEvent)) {
            return;
        }
        log.warn(
                "{} 공개키 목록을 받지 못해 이전에 받은 목록을 사용합니다. remainingMillis={}, cause={}",
                provider.name(),
                outageEvent.getRemainingTime(),
                outageEvent.getException().getMessage());
    }

    <C extends SecurityContext> void logRateLimited(Event<RateLimitedJWKSetSource<C>, C> event) {
        long now = clock.millis();
        long nextLogAt = nextRateLimitedLogAtMillis.get();
        if (now < nextLogAt) {
            return;
        }
        long followingLogAt = now + rateLimitedLogIntervalMillis;
        if (!nextRateLimitedLogAtMillis.compareAndSet(nextLogAt, followingLogAt)) {
            return;
        }
        log.warn("{} 공개키 목록 재조회 제한에 걸려 조회 없이 ID Token 검증에 실패했습니다.", provider.name());
    }
}
