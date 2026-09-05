package com.chalkak.backend.auth.infrastructure.infra.signup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ConsumedSignupTokenCleanupSchedulerTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Autowired
    private ConsumedSignupTokenCleanupScheduler consumedSignupTokenCleanupScheduler;

    @Autowired
    private ConsumedSignupTokenRepository consumedSignupTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
    }

    @Test
    @DisplayName("만료된 회원가입 토큰 기록만 지우고 아직 유효한 기록은 남긴다")
    void deleteExpiredTokens_expiredRecord_deletesOnlyExpired() {
        // Given
        String expiredJti = consume(NOW.minus(Duration.ofSeconds(1)));
        String liveJti = consume(NOW.plus(Duration.ofMinutes(5)));
        flushAndClear();

        // When
        consumedSignupTokenCleanupScheduler.deleteExpiredTokens();
        flushAndClear();

        // Then
        assertThat(isConsumed(expiredJti)).isFalse();
        assertThat(isConsumed(liveJti)).isTrue();
    }

    @Test
    @DisplayName("만료 시각이 정확히 현재와 같은 기록은 아직 지우지 않는다")
    void deleteExpiredTokens_expiringExactlyNow_keepsRecord() {
        // Given
        String boundaryJti = consume(NOW);
        flushAndClear();

        // When
        consumedSignupTokenCleanupScheduler.deleteExpiredTokens();
        flushAndClear();

        // Then
        assertThat(isConsumed(boundaryJti)).isTrue();
    }

    private String consume(Instant expiresAt) {
        String jti = UUID.randomUUID().toString();
        consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(jti, expiresAt));
        return jti;
    }

    /** 지워졌다면 같은 jti를 다시 소진할 수 있고, 남아 있다면 거절된다. */
    private boolean isConsumed(String jti) {
        boolean consumed = consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(jti, NOW.plus(Duration.ofMinutes(5))));
        return !consumed;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
