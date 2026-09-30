package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDevice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeviceJpaRepository extends JpaRepository<PushDevice, UUID> {

    // 회차 잠금(단일 키)과 다른 네임스페이스를 사용한다. 토큰 원문은 잠금·조회에 쓰지 않는다.
    @Query(value = "SELECT pg_advisory_xact_lock(1, hashtext(:tokenHash))", nativeQuery = true)
    void lockToken(@Param("tokenHash") String tokenHash);

    @Query("""
            SELECT device.session.id FROM PushDevice device
            WHERE device.fcmTokenHash = :tokenHash AND device.disabledAt IS NULL
            """)
    Optional<UUID> findActiveSessionIdByTokenHash(@Param("tokenHash") String tokenHash);

    Optional<PushDevice> findBySessionId(UUID sessionId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE PushDevice device
            SET device.fcmToken = NULL, device.fcmTokenHash = NULL,
                device.disabledAt = :disabledAt, device.updatedAt = :disabledAt
            WHERE device.fcmTokenHash = :tokenHash AND device.disabledAt IS NULL
                AND device.session.id <> :sessionId
            """)
    int disableOtherSessionByTokenHash(
            @Param("tokenHash") String tokenHash,
            @Param("sessionId") UUID sessionId,
            @Param("disabledAt") Instant disabledAt
    );
}
