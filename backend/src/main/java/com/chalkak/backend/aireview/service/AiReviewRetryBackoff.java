package com.chalkak.backend.aireview.service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/**
 * 자동 재시도 전에 기다릴 시간을 정한다. 첫 재시도는 10초, 그 뒤는 30초를 기본으로 하고 같은 시각에 실패한 행들이 한꺼번에 다시
 * 몰리지 않도록 ±20% 범위에서 흩는다. 외부 서비스가 더 오래 기다리라고 알려 줬다면 그 시간을 따르되 60초를 넘기지 않는다.
 */
public class AiReviewRetryBackoff {

    private static final Duration FIRST_DELAY = Duration.ofSeconds(10);
    private static final Duration LATER_DELAY = Duration.ofSeconds(30);
    private static final Duration MAX_DELAY = Duration.ofSeconds(60);
    private static final double JITTER_RATIO = 0.2;

    private final DoubleSupplier jitter;

    /**
     * @param jitter
     *            0 이상 1 미만의 값. 0.5가 기본 대기 시간이고 0이 -20%, 1에 가까울수록 +20%에 가깝다.
     */
    public AiReviewRetryBackoff(DoubleSupplier jitter) {
        this.jitter = jitter;
    }

    /**
     * @param roundAttempt
     *            방금 실패한 시도가 이번 회차에서 몇 번째 시도였는지(1부터)
     * @param retryAfter
     *            외부 서비스가 알려 준 최소 대기 시간. 없으면 백오프만 따른다.
     */
    public RetryDelay delayAfter(int roundAttempt, Optional<Duration> retryAfter) {
        Duration base = roundAttempt <= 1 ? FIRST_DELAY : LATER_DELAY;
        double factor = 1 + JITTER_RATIO * (2 * jitter.getAsDouble() - 1);
        Duration backoff = Duration.ofMillis(Math.round(base.toMillis() * factor));
        if (retryAfter.isEmpty() || retryAfter.get().compareTo(backoff) <= 0) {
            return new RetryDelay(backoff, false);
        }
        Duration requested = retryAfter.get();
        return new RetryDelay(requested.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : requested, true);
    }

    /**
     * @param delay
     *            실제로 적용할 대기 시간
     * @param retryAfterUsed
     *            외부 서비스가 알려 준 시간이 백오프보다 길어 대기 시간을 결정했는지
     */
    public record RetryDelay(Duration delay, boolean retryAfterUsed) {

        public static final RetryDelay NONE = new RetryDelay(Duration.ZERO, false);
    }
}
