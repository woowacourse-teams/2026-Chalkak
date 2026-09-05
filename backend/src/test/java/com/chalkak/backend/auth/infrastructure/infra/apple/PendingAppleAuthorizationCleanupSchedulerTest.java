package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.auth.service.AppleAuthorizationCipher;
import com.chalkak.backend.auth.service.AppleTokenClient;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PendingAppleAuthorizationCleanupSchedulerTest
        extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");
    private static final String EXPIRED_ENCRYPTED_TOKEN = "expired-encrypted-token";
    private static final String BOUNDARY_ENCRYPTED_TOKEN = "boundary-encrypted-token";
    private static final String LIVE_ENCRYPTED_TOKEN = "live-encrypted-token";
    private static final String FAILED_ENCRYPTED_TOKEN = "failed-encrypted-token";

    @Autowired
    private PendingAppleAuthorizationCleanupScheduler cleanupScheduler;

    @Autowired
    private PendingAppleAuthorizationRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private AppleAuthorizationCipher authorizationCipher;

    @MockitoBean
    private AppleTokenClient appleTokenClient;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
        given(clock.instant()).willReturn(NOW);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("만료된 임시 Apple RT를 트랜잭션 밖에서 폐기한 뒤 삭제한다")
    void revokeAndDeleteExpiredAuthorizations_expiredRecords_revokesAndDeletes() {
        // Given
        UUID expiredUploadId = save(
                EXPIRED_ENCRYPTED_TOKEN,
                NOW.minus(Duration.ofSeconds(1)));
        UUID boundaryUploadId = save(BOUNDARY_ENCRYPTED_TOKEN, NOW);
        UUID liveUploadId = save(
                LIVE_ENCRYPTED_TOKEN,
                NOW.plus(Duration.ofMinutes(5)));
        given(authorizationCipher.decrypt(EXPIRED_ENCRYPTED_TOKEN))
                .willReturn("expired-refresh-token");
        given(authorizationCipher.decrypt(BOUNDARY_ENCRYPTED_TOKEN))
                .willReturn("boundary-refresh-token");
        willAnswer(invocation -> {
                    assertThat(TransactionSynchronizationManager
                            .isActualTransactionActive()).isFalse();
                    return null;
                })
                .given(appleTokenClient)
                .revokeRefreshToken("expired-refresh-token");

        // When
        cleanupScheduler.revokeAndDeleteExpiredAuthorizations();

        // Then
        verify(appleTokenClient).revokeRefreshToken("expired-refresh-token");
        verify(appleTokenClient).revokeRefreshToken("boundary-refresh-token");
        assertThat(repository.findByUploadId(expiredUploadId)).isEmpty();
        assertThat(repository.findByUploadId(boundaryUploadId)).isEmpty();
        assertThat(repository.findByUploadId(liveUploadId)).isPresent();
    }

    @Test
    @DisplayName("한 RT의 폐기가 실패하면 해당 행을 남기고 나머지 RT는 계속 처리한다")
    void revokeAndDeleteExpiredAuthorizations_revocationFailure_retainsAndContinues() {
        // Given
        UUID failedUploadId = save(
                FAILED_ENCRYPTED_TOKEN,
                NOW.minus(Duration.ofMinutes(2)));
        UUID succeededUploadId = save(
                EXPIRED_ENCRYPTED_TOKEN,
                NOW.minus(Duration.ofMinutes(1)));
        given(authorizationCipher.decrypt(FAILED_ENCRYPTED_TOKEN))
                .willReturn("failed-refresh-token");
        given(authorizationCipher.decrypt(EXPIRED_ENCRYPTED_TOKEN))
                .willReturn("expired-refresh-token");
        willThrow(new IllegalStateException("Apple 통신 실패"))
                .given(appleTokenClient)
                .revokeRefreshToken("failed-refresh-token");

        // When
        cleanupScheduler.revokeAndDeleteExpiredAuthorizations();

        // Then
        verify(appleTokenClient).revokeRefreshToken("expired-refresh-token");
        assertThat(repository.findByUploadId(failedUploadId)).isPresent();
        assertThat(repository.findByUploadId(succeededUploadId)).isEmpty();
    }

    @Test
    @DisplayName("유효한 임시 Apple 인증 정보만 있으면 복호화와 폐기를 요청하지 않는다")
    void revokeAndDeleteExpiredAuthorizations_liveRecord_doesNothing() {
        // Given
        UUID liveUploadId = save(
                LIVE_ENCRYPTED_TOKEN,
                NOW.plus(Duration.ofMinutes(5)));

        // When
        cleanupScheduler.revokeAndDeleteExpiredAuthorizations();

        // Then
        verifyNoInteractions(authorizationCipher, appleTokenClient);
        assertThat(repository.findByUploadId(liveUploadId)).isPresent();
    }

    private UUID save(String encryptedRefreshToken, Instant expiresAt) {
        UUID uploadId = UUID.randomUUID();
        repository.save(PendingAppleAuthorization.create(
                uploadId,
                encryptedRefreshToken,
                expiresAt));
        return uploadId;
    }
}
