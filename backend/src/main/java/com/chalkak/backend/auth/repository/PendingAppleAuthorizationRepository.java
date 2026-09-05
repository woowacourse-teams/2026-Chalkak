package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PendingAppleAuthorizationRepository {

    PendingAppleAuthorization save(PendingAppleAuthorization authorization);

    Optional<PendingAppleAuthorization> findByUploadId(UUID uploadId);

    Optional<PendingAppleAuthorization> findByUploadIdForUpdate(UUID uploadId);

    List<PendingAppleAuthorization> findAllExpiredAtOrBefore(Instant now);

    void delete(PendingAppleAuthorization authorization);
}
