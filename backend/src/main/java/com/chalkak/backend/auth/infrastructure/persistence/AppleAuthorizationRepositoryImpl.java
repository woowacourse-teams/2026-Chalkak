package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppleAuthorizationRepositoryImpl implements AppleAuthorizationRepository {

    private final AppleAuthorizationJpaRepository repository;

    @Override
    public AppleAuthorization save(AppleAuthorization authorization) {
        return repository.save(authorization);
    }

    @Override
    public List<AppleAuthorization> findAllBySocialAccountId(UUID socialAccountId) {
        return repository.findAllBySocialAccountId(socialAccountId);
    }

    @Override
    public void deleteAllBySocialAccountId(UUID socialAccountId) {
        repository.deleteAllBySocialAccountId(socialAccountId);
    }
}
