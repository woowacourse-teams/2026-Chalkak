package com.chalkak.backend.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    private final RefreshTokenPolicy policy = new RefreshTokenPolicy(
            Duration.ofDays(30),
            Duration.ofDays(90));

    @Test
    @DisplayName("비활동 만료가 절대 만료보다 이르면 비활동 만료를 그대로 쓴다")
    void nextExpiresAt_beforeAbsoluteExpiry_returnsInactivityExpiry() {
        // given
        Instant absoluteExpiresAt = NOW.plus(Duration.ofDays(30)).plusSeconds(1);

        // when
        Instant nextExpiresAt = policy.nextExpiresAt(NOW, absoluteExpiresAt);

        // then
        assertThat(nextExpiresAt).isEqualTo(NOW.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("비활동 만료가 절대 만료와 정확히 같으면 그 시각을 그대로 쓴다")
    void nextExpiresAt_exactlyAtAbsoluteExpiry_returnsAbsoluteExpiry() {
        // given
        Instant absoluteExpiresAt = NOW.plus(Duration.ofDays(30));

        // when
        Instant nextExpiresAt = policy.nextExpiresAt(NOW, absoluteExpiresAt);

        // then
        assertThat(nextExpiresAt).isEqualTo(absoluteExpiresAt);
    }

    @Test
    @DisplayName("비활동 만료가 절대 만료를 넘으면 절대 만료로 자른다")
    void nextExpiresAt_afterAbsoluteExpiry_returnsAbsoluteExpiry() {
        // given
        Instant absoluteExpiresAt = NOW.plus(Duration.ofDays(30)).minusSeconds(1);

        // when
        Instant nextExpiresAt = policy.nextExpiresAt(NOW, absoluteExpiresAt);

        // then
        assertThat(nextExpiresAt).isEqualTo(absoluteExpiresAt);
    }

    @Test
    @DisplayName("절대 만료 시각은 기준 시각에 절대 만료 기간을 더한 값이다")
    void absoluteExpiresAt_now_returnsNowPlusAbsoluteExpiration() {
        // when
        Instant absoluteExpiresAt = policy.absoluteExpiresAt(NOW);

        // then
        assertThat(absoluteExpiresAt).isEqualTo(NOW.plus(Duration.ofDays(90)));
    }
}
