package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRefreshTokenJpaRepository extends JpaRepository<UserRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT refreshToken
            FROM UserRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<UserRefreshToken> findByTokenHashForUpdate(
            @Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE UserRefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.sessionId = :sessionId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeSession(
            @Param("sessionId") UUID sessionId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            UPDATE UserRefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.user.id = :ownerId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeAllByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            DELETE FROM UserRefreshToken refreshToken
            WHERE refreshToken.absoluteExpiresAt < :now
               OR refreshToken.revokedAt < :revokedThreshold
            """)
    int deleteUnusableBefore(
            @Param("now") Instant now,
            @Param("revokedThreshold") Instant revokedThreshold);
}
