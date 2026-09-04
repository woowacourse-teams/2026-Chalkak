package com.chalkak.backend.auth.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * 리프레시 토큰의 수명 정책. 비활동 만료는 회전할 때마다 갱신하지만 절대 만료는 최초 로그인 시점에
 * 고정하므로, 두 값을 한 곳에서 계산해 회전 경로마다 규칙이 어긋나지 않게 한다.
 */
public record RefreshTokenPolicy(
        Duration inactivityExpiration,
        Duration absoluteExpiration
) {

    public RefreshTokenPolicy {
        validatePositive(inactivityExpiration);
        validatePositive(absoluteExpiration);
        if (inactivityExpiration.compareTo(absoluteExpiration) > 0) {
            throw new IllegalArgumentException(
                    "Refresh token inactivity expiration must not exceed absolute expiration");
        }
    }

    /**
     * 회전 후의 비활동 만료 시각. 절대 만료를 넘어서면 회전이 로그인 수명을 연장하는 셈이 되므로
     * 절대 만료 시각으로 자른다.
     */
    public Instant nextExpiresAt(Instant now, Instant absoluteExpiresAt) {
        Instant expiresAt = now.plus(inactivityExpiration);
        if (expiresAt.isAfter(absoluteExpiresAt)) {
            return absoluteExpiresAt;
        }
        return expiresAt;
    }

    /** 최초 로그인 시점에 한 번 정해지는 절대 만료 시각. */
    public Instant absoluteExpiresAt(Instant now) {
        return now.plus(absoluteExpiration);
    }

    private static void validatePositive(Duration duration) {
        if (duration == null || !duration.isPositive()) {
            throw new IllegalArgumentException(
                    "Refresh token durations must be positive");
        }
    }
}
