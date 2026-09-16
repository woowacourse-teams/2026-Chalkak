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
     * 한 신원에 여러 행이 남을 수 있어 정렬이 필요하다. 생성 시각이 같을 때의 순서는 식별자가
     * 정하는데, 같은 밀리초 안에서까지 생성 순서와 맞는다는 보장은 없다. 결과가 하나로 정해지기만
     * 하면 충분하다. 고르지 않은 행은 정리 스케줄러가 폐기한다.
     */
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
