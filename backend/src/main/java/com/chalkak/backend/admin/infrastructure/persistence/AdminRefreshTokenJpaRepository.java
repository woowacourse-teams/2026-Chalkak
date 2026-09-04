package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminRefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminRefreshTokenJpaRepository extends JpaRepository<AdminRefreshToken, UUID> {

    @Query("""
            SELECT refreshToken.sessionId
            FROM AdminRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<UUID> findSessionIdByTokenHash(
            @Param("tokenHash") String tokenHash);

    @Query("""
            SELECT DISTINCT refreshToken.sessionId
            FROM AdminRefreshToken refreshToken
            WHERE refreshToken.admin.id = :ownerId
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
            FROM AdminRefreshToken refreshToken
            WHERE refreshToken.tokenHash = :tokenHash
            """)
    Optional<AdminRefreshToken> findByTokenHash(
            @Param("tokenHash") String tokenHash);

    @Modifying
    @Query("""
            UPDATE AdminRefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.sessionId = :sessionId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeSession(
            @Param("sessionId") UUID sessionId,
            @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("""
            UPDATE AdminRefreshToken refreshToken
            SET refreshToken.revokedAt = :revokedAt
            WHERE refreshToken.admin.id = :ownerId
              AND refreshToken.revokedAt IS NULL
            """)
    int revokeAllByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("revokedAt") Instant revokedAt);

    /**
     * 절대 만료가 지난 토큰을 지운다. 폐기 조건과 한 문장으로 묶으면 서로 다른 두 컬럼에 걸린 OR가
     * 되어 어느 인덱스도 타지 못하고 전체 스캔이 되므로, 조건마다 문장을 나눠 각자의 인덱스를 쓴다.
     */
    @Modifying
    @Query("""
            DELETE FROM AdminRefreshToken refreshToken
            WHERE refreshToken.absoluteExpiresAt < :now
            """)
    int deleteAbsolutelyExpiredBefore(@Param("now") Instant now);

    /** 폐기된 지 충분히 오래된 토큰을 지운다. 위와 같은 이유로 절대 만료 조건과 나눠 둔다. */
    @Modifying
    @Query("""
            DELETE FROM AdminRefreshToken refreshToken
            WHERE refreshToken.revokedAt < :revokedThreshold
            """)
    int deleteRevokedBefore(@Param("revokedThreshold") Instant revokedThreshold);
}
