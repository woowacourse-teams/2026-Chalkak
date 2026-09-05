package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppleAuthorizationJpaRepository
        extends JpaRepository<AppleAuthorization, UUID> {

    List<AppleAuthorization> findAllBySocialAccountId(UUID socialAccountId);

    void deleteAllBySocialAccountId(UUID socialAccountId);
}
