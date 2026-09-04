package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PendingAppleAuthorizationJpaRepository
        extends JpaRepository<PendingAppleAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PendingAppleAuthorization> findByUploadId(UUID uploadId);

    List<PendingAppleAuthorization> findAllByExpiresAtLessThanEqualOrderByExpiresAtAsc(
            Instant now
    );
}
