package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AppleAuthorizationJpaRepository
        extends JpaRepository<AppleAuthorization, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AppleAuthorization> findBySocialAccountIdAndClientId(
            UUID socialAccountId,
            String clientId);

    List<AppleAuthorization> findAllBySocialAccountId(UUID socialAccountId);

    void deleteAllBySocialAccountId(UUID socialAccountId);
}
