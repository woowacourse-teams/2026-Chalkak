package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PendingAppleAuthorizationRepositoryImpl
        implements PendingAppleAuthorizationRepository {

    private final PendingAppleAuthorizationJpaRepository repository;

    @Override
    public PendingAppleAuthorization save(PendingAppleAuthorization authorization) {
        return repository.save(authorization);
    }

    @Override
    public Optional<PendingAppleAuthorization> findByUploadId(UUID uploadId) {
        return repository.findById(uploadId);
    }

    @Override
    public Optional<PendingAppleAuthorization> findByUploadIdForUpdate(
            UUID uploadId
    ) {
        return repository.findByUploadId(uploadId);
    }

    @Override
    public void delete(PendingAppleAuthorization authorization) {
        repository.delete(authorization);
    }

    @Override
    public void deleteAllExpiredAtOrBefore(Instant now) {
        repository.deleteByExpiresAtLessThanEqual(now);
    }
}
