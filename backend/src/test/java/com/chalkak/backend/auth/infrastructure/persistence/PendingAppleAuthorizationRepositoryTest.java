package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PendingAppleAuthorizationRepositoryTest extends IntegrationTestSupport {

    private static final String SUBJECT_HMAC = "a".repeat(64);
    private static final String OTHER_SUBJECT_HMAC = "b".repeat(64);
    private static final Instant EXPIRES_AT =
            Instant.parse("2026-09-04T00:05:00Z");
    private static final Instant BEFORE_EXPIRY =
            EXPIRES_AT.minus(Duration.ofMinutes(1));

    @Autowired
    private PendingAppleAuthorizationRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("한 신원에 만료 전 행이 여러 개면 가장 나중에 만든 행을 반환한다")
    void findLatestUnexpiredBySubjectHmac_multipleRows_returnsLatest() {
        // Given
        // 재로그인으로 행이 쌓여도 정식 보관으로 옮길 대상은 마지막 교환 결과 하나뿐이다.
        save(SUBJECT_HMAC, "first-token", EXPIRES_AT);
        save(SUBJECT_HMAC, "second-token", EXPIRES_AT.plus(Duration.ofMinutes(10)));
        save(SUBJECT_HMAC, "third-token", EXPIRES_AT);

        // When
        Optional<PendingAppleAuthorization> found = repository
                .findLatestUnexpiredBySubjectHmac(SUBJECT_HMAC, BEFORE_EXPIRY);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEncryptedRefreshToken()).isEqualTo("third-token");
    }

    @Test
    @DisplayName("만료 시각이 지난 행은 반환하지 않는다")
    void findLatestUnexpiredBySubjectHmac_atExpiryBoundary_excludesExpired() {
        // Given
        save(SUBJECT_HMAC, "boundary-token", EXPIRES_AT);

        // When & Then
        assertThat(repository.findLatestUnexpiredBySubjectHmac(
                SUBJECT_HMAC,
                EXPIRES_AT.minusMillis(1))).isPresent();
        assertThat(repository.findLatestUnexpiredBySubjectHmac(
                SUBJECT_HMAC,
                EXPIRES_AT)).isEmpty();
        assertThat(repository.findLatestUnexpiredBySubjectHmac(
                SUBJECT_HMAC,
                EXPIRES_AT.plusMillis(1))).isEmpty();
    }

    @Test
    @DisplayName("다른 신원의 임시 인증 정보는 반환하지 않는다")
    void findLatestUnexpiredBySubjectHmac_otherSubjectHmac_returnsEmpty() {
        // Given
        save(OTHER_SUBJECT_HMAC, "other-token", EXPIRES_AT);

        // When
        Optional<PendingAppleAuthorization> found = repository
                .findLatestUnexpiredBySubjectHmac(SUBJECT_HMAC, BEFORE_EXPIRY);

        // Then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("잠금 조회도 한 신원의 가장 나중에 만든 만료 전 행을 반환한다")
    void findLatestUnexpiredBySubjectHmacForUpdate_multipleRows_returnsLatest() {
        // Given
        save(SUBJECT_HMAC, "first-token", EXPIRES_AT);
        save(SUBJECT_HMAC, "second-token", EXPIRES_AT);

        // When
        Optional<PendingAppleAuthorization> found = repository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        SUBJECT_HMAC,
                        BEFORE_EXPIRY);

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getEncryptedRefreshToken())
                .isEqualTo("second-token");
    }

    private void save(
            String subjectHmac,
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        repository.save(PendingAppleAuthorization.create(
                subjectHmac,
                encryptedRefreshToken,
                expiresAt));
        entityManager.flush();
        entityManager.clear();
    }
}
