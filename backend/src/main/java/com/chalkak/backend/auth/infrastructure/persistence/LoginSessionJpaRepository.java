package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.LoginSession;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionJpaRepository extends JpaRepository<LoginSession, UUID> {

    Optional<LoginSession> findByIdAndUserId(UUID sessionId, UUID userId);
}
