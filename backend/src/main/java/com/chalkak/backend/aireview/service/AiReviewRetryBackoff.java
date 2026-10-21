package com.chalkak.backend.aireview.service;

import java.time.Duration;
import java.util.function.DoubleSupplier;

/**
 * 자동 재시도 전에 기다릴 시간을 정한다. 첫 재시도는 10초, 그 뒤는 30초를 기본으로 하고 같은 시각에 실패한 행들이 한꺼번에 다시
 * 몰리지 않도록 ±20% 범위에서 흩는다.
 */
public class AiReviewRetryBackoff {

    private static final Duration FIRST_DELAY = Duration.ofSeconds(10);
    private static final Duration LATER_DELAY = Duration.ofSeconds(30);
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
     */
    public Duration delayAfter(int roundAttempt) {
        Duration base = roundAttempt <= 1 ? FIRST_DELAY : LATER_DELAY;
        double factor = 1 + JITTER_RATIO * (2 * jitter.getAsDouble() - 1);
        return Duration.ofMillis(Math.round(base.toMillis() * factor));
    }
}
