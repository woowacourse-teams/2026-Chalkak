package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PendingAppleAuthorizationRepository {

    PendingAppleAuthorization save(PendingAppleAuthorization authorization);

    Optional<PendingAppleAuthorization> findLatestUnexpiredBySubjectHmacForUpdate(
            String subjectHmac,
            Instant now
    );

    List<PendingAppleAuthorization> findAllExpiredAtOrBefore(Instant now);

    void delete(PendingAppleAuthorization authorization);
}
