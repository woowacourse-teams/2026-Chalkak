package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRefreshTokenJpaRepository extends JpaRepository<UserRefreshToken, UUID> {

    @Query("""
            SELECT refreshToken.sessionId
            FROM UserRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<UUID> findSessionIdByTokenHash(
            @Param("tokenHash") String tokenHash);

    @Query("""
            SELECT DISTINCT refreshToken.sessionId
            FROM UserRefreshToken refreshToken
            WHERE refreshToken.user.id = :ownerId
              AND refreshToken.revokedAt IS NULL
            ORDER BY refreshToken.sessionId
            """)
    List<UUID> findLiveSessionIdsByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * 계보 하나를 트랜잭션이 끝날 때까지 잠근다. 행 잠금과 달리 아직 없는 행까지 덮으므로, 회전이
     * 만들어 낼 후속 토큰도 같은 잠금 아래에 들어온다. 별도의 해제 호출은 없고 커밋이나 롤백이 푼다.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:sessionId AS text)))",
            nativeQuery = true)
    void lockSession(@Param("sessionId") UUID sessionId);

    @Query("""
            SELECT refreshToken
            FROM UserRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<UserRefreshToken> findByTokenHash(
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
