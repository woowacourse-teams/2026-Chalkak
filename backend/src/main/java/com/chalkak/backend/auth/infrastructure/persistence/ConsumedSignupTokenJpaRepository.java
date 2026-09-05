package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumedSignupTokenJpaRepository
        extends JpaRepository<ConsumedSignupToken, String> {

    long deleteByExpiresAtBefore(Instant instant);
}
