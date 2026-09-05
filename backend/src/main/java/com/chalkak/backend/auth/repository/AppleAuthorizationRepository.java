package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import java.util.List;
import java.util.UUID;

public interface AppleAuthorizationRepository {

    AppleAuthorization save(AppleAuthorization authorization);

    List<AppleAuthorization> findAllBySocialAccountId(UUID socialAccountId);

    void deleteAllBySocialAccountId(UUID socialAccountId);
}
