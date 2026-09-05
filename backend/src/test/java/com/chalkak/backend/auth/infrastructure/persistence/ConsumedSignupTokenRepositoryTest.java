package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class ConsumedSignupTokenRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ConsumedSignupTokenRepository consumedSignupTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("처음 소진하는 jti는 저장하고 성공을 반환한다")
    void consumeIfAbsent_newJti_returnsTrue() {
        // Given
        String jti = UUID.randomUUID().toString();

        // When
        boolean firstUse = consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(jti, futureInstant()));

        // Then
        assertThat(firstUse).isTrue();
    }

    @Test
    @DisplayName("같은 jti를 다시 소진하려 하면 저장하지 않고 실패를 반환한다")
    void consumeIfAbsent_alreadyConsumedJti_returnsFalse() {
        // Given
        String jti = UUID.randomUUID().toString();
        consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(jti, futureInstant()));
        flushAndClear();

        // When
        boolean secondUse = consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(jti, futureInstant()));

        // Then
        assertThat(secondUse).isFalse();
    }

    @Test
    @DisplayName("만료된 소진 기록만 정리하고 아직 유효한 기록은 남긴다")
    void deleteAllExpiredBefore_mixedRecords_deletesOnlyExpiredOnes() {
        // Given
        String expiredJti = UUID.randomUUID().toString();
        String activeJti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(expiredJti, now.minus(Duration.ofMinutes(1))));
        consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(activeJti, now.plus(Duration.ofMinutes(5))));
        flushAndClear();

        // When
        consumedSignupTokenRepository.deleteAllExpiredBefore(now);
        flushAndClear();

        // Then
        assertThat(consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(expiredJti, futureInstant())))
                .isTrue();
        assertThat(consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(activeJti, futureInstant())))
                .isFalse();
    }

    private Instant futureInstant() {
        return Instant.now().plus(Duration.ofMinutes(5));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
