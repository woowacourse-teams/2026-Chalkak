package com.chalkak.backend.auth.infrastructure.infra.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.nimbusds.jose.jwk.source.RateLimitedJWKSetSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.events.Event;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class OidcJwkSetEventLoggerTest {

    private static final String RATE_LIMITED_MESSAGE = "KAKAO 공개키 목록 재조회 제한에 걸려 조회 없이 ID Token 검증에 실패했습니다.";
    private static final long FIRST_LOGGED_AT = 1_000_000L;

    @Test
    @DisplayName("재조회 제한 알림은 구간 직전까지 다시 남기지 않고 구간이 지나면 다시 남긴다")
    void logRateLimited_repeatedAcrossInterval_logsOncePerInterval(CapturedOutput output) {
        // Given
        Clock clock = mock(Clock.class);
        given(clock.millis()).willReturn(
                FIRST_LOGGED_AT,
                FIRST_LOGGED_AT + 29_999,
                FIRST_LOGGED_AT + 30_000);
        OidcJwkSetEventLogger eventLogger = new OidcJwkSetEventLogger(
                SocialProvider.KAKAO,
                Duration.ofSeconds(30),
                clock);
        Event<RateLimitedJWKSetSource<SecurityContext>, SecurityContext> event = rateLimitedEvent();

        // When
        eventLogger.logRateLimited(event);
        eventLogger.logRateLimited(event);
        String outputBeforeInterval = output.getOut();
        eventLogger.logRateLimited(event);

        // Then
        assertThat(outputBeforeInterval).containsOnlyOnce(RATE_LIMITED_MESSAGE);
        assertThat(output.getOut().split(RATE_LIMITED_MESSAGE, -1)).hasSize(3);
    }

    @SuppressWarnings("unchecked")
    private Event<RateLimitedJWKSetSource<SecurityContext>, SecurityContext> rateLimitedEvent() {
        return mock(Event.class);
    }
}
