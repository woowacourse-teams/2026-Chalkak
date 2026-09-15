package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsumedSignupTokenJpaRepository
        extends JpaRepository<ConsumedSignupToken, String> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO consumed_signup_tokens (jti, expires_at)
                    VALUES (:jti, :expiresAt)
                    ON CONFLICT (jti) DO NOTHING
                    """,
            nativeQuery = true
    )
    int createIfAbsent(
            @Param("jti") String jti,
            @Param("expiresAt") Instant expiresAt
    );

    long deleteByExpiresAtBefore(Instant instant);
}
