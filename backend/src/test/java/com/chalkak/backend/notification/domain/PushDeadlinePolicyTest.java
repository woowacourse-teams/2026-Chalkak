package com.chalkak.backend.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PushDeadlinePolicyTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-10-12T14:50:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-10-12T15:20:00Z");

    @Test
    @DisplayName("날짜가 바뀌어도 발생 시각부터 정확히 30분 뒤로 기한을 계산한다")
    void calculateExpiresAt_crossesKstMidnight_preservesThirtyMinutes() {
        // When
        Instant expiresAt = PushDeadlinePolicy.calculateExpiresAt(OCCURRED_AT);

        // Then
        assertThat(expiresAt).isEqualTo(EXPIRES_AT);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("만료 시각 직전까지 발송할 수 있고 정각부터 만료다")
    void isExpiredAt_deadlineBoundary_expiresAtDeadline(long nanos) {
        // Given
        Instant now = EXPIRES_AT.plusNanos(nanos);

        // When
        boolean expired = PushDeadlinePolicy.isExpiredAt(EXPIRES_AT, now);

        // Then
        assertThat(expired).isEqualTo(nanos >= 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 1})
    @DisplayName("발생 시각과 만료 시각의 차이가 정확히 30분인 경우만 유효하다")
    void isValidLifetime_lifetimeBoundary_acceptsExactlyThirtyMinutes(long nanos) {
        // Given
        Instant expiresAt = EXPIRES_AT.plusNanos(nanos);

        // When
        boolean valid = PushDeadlinePolicy.isValidLifetime(OCCURRED_AT, expiresAt);

        // Then
        assertThat(valid).isEqualTo(nanos == 0);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 3600})
    @DisplayName("역순·동일 시각·늘어난 기한은 유효한 발송 기한이 아니다")
    void isValidLifetime_invalidInterval_rejectsLifetime(long seconds) {
        // Given
        Instant expiresAt = OCCURRED_AT.plusSeconds(seconds);

        // When
        boolean valid = PushDeadlinePolicy.isValidLifetime(OCCURRED_AT, expiresAt);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("극단적인 메시지 시각도 덧셈 오버플로 없이 잘못된 기한으로 판정한다")
    void isValidLifetime_extremeInstants_rejectsWithoutOverflow() {
        // When
        boolean valid = PushDeadlinePolicy.isValidLifetime(Instant.MAX, Instant.MIN);

        // Then
        assertThat(valid).isFalse();
    }
}
