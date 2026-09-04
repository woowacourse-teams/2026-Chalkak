package com.chalkak.backend.auth.infrastructure.infra.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
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
class PendingAppleAuthorizationCleanupSchedulerTest
        extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-04T00:00:00Z");

    @Autowired
    private PendingAppleAuthorizationCleanupScheduler cleanupScheduler;

    @Autowired
    private PendingAppleAuthorizationRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
    }

    @Test
    @DisplayName("만료된 임시 Apple 인증 정보만 삭제한다")
    void deleteExpiredAuthorizations_deletesOnlyExpiredRecords() {
        UUID expiredUploadId = save(NOW.minus(Duration.ofSeconds(1)));
        UUID boundaryUploadId = save(NOW);
        UUID liveUploadId = save(NOW.plus(Duration.ofMinutes(5)));
        flushAndClear();

        cleanupScheduler.deleteExpiredAuthorizations();
        flushAndClear();

        assertThat(repository.findByUploadId(expiredUploadId)).isEmpty();
        assertThat(repository.findByUploadId(boundaryUploadId)).isEmpty();
        assertThat(repository.findByUploadId(liveUploadId)).isPresent();
    }

    private UUID save(Instant expiresAt) {
        UUID uploadId = UUID.randomUUID();
        repository.save(PendingAppleAuthorization.create(
                uploadId,
                "encrypted-refresh-token",
                expiresAt));
        return uploadId;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
