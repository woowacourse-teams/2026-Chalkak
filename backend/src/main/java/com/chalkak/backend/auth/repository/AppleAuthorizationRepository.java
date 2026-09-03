package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppleAuthorizationRepository {

    AppleAuthorization save(AppleAuthorization authorization);

    Optional<AppleAuthorization> findBySocialAccountIdAndClientIdForUpdate(
            UUID socialAccountId,
            String clientId);

    List<AppleAuthorization> findAllBySocialAccountId(UUID socialAccountId);

    void deleteAllBySocialAccountId(UUID socialAccountId);
}
