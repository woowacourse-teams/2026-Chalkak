package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PendingAppleAuthorizationJpaRepository
        extends JpaRepository<PendingAppleAuthorization, UUID> {

    /**
     * 한 신원에 여러 행이 남을 수 있어 정렬이 필요하다. 식별자는 시간 순서가 보장되는
     * UUIDv7이므로, 생성 시각이 같아도 최신 행이 하나로 정해진다.
     */
    @Query("""
            SELECT authorization
            FROM PendingAppleAuthorization authorization
            WHERE authorization.subjectHmac = :subjectHmac
              AND authorization.expiresAt > :now
            ORDER BY authorization.createdAt DESC, authorization.id DESC
            LIMIT 1
            """)
    Optional<PendingAppleAuthorization> findLatestUnexpired(
            String subjectHmac,
            Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT authorization
            FROM PendingAppleAuthorization authorization
            WHERE authorization.subjectHmac = :subjectHmac
              AND authorization.expiresAt > :now
            ORDER BY authorization.createdAt DESC, authorization.id DESC
            LIMIT 1
            """)
    Optional<PendingAppleAuthorization> findLatestUnexpiredForUpdate(
            String subjectHmac,
            Instant now
    );

    List<PendingAppleAuthorization> findAllByExpiresAtLessThanEqualOrderByExpiresAtAsc(
            Instant now
    );
}
