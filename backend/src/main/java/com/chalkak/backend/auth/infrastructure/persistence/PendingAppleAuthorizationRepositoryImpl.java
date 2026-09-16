package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    public Optional<PendingAppleAuthorization> findLatestUnexpiredBySubjectHmacForUpdate(
            String subjectHmac,
            Instant now
    ) {
        return repository.findLatestUnexpiredForUpdate(subjectHmac, now);
    }

    @Override
    public List<PendingAppleAuthorization> findAllExpiredAtOrBefore(Instant now) {
        return repository.findAllByExpiresAtLessThanEqualOrderByExpiresAtAsc(now);
    }

    @Override
    public void delete(PendingAppleAuthorization authorization) {
        repository.delete(authorization);
    }
}
