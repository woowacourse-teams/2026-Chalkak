package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDelivery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushDeliveryJpaRepository extends JpaRepository<PushDelivery, UUID> {
    Optional<PushDelivery> findByEventIdAndUserId(UUID eventId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO push_deliveries (event_id, user_id)
            SELECT :eventId, :userId WHERE EXISTS (SELECT 1 FROM users WHERE id = :userId)
            ON CONFLICT (event_id, user_id) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(@Param("eventId") UUID eventId, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE push_deliveries SET status = 'PROCESSING', lease_token = :leaseToken,
                lease_until = clock_timestamp() + :leaseMillis * INTERVAL '1 millisecond',
                updated_at = clock_timestamp()
            WHERE event_id = :eventId AND user_id = :userId AND status <> 'COMPLETED'
                AND (lease_until IS NULL OR lease_until <= clock_timestamp())
            """, nativeQuery = true)
    int claim(
            @Param("eventId") UUID eventId,
            @Param("userId") UUID userId,
            @Param("leaseToken") UUID leaseToken,
            @Param("leaseMillis") long leaseMillis
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE push_deliveries
            SET lease_until = clock_timestamp() + :leaseMillis * INTERVAL '1 millisecond',
                updated_at = clock_timestamp()
            WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                AND lease_until > clock_timestamp()
            """, nativeQuery = true)
    int renew(
            @Param("id") UUID id,
            @Param("leaseToken") UUID leaseToken,
            @Param("leaseMillis") long leaseMillis
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE push_deliveries
            SET device_results = jsonb_set(device_results, ARRAY[CAST(:deviceId AS text)],
                CAST(:result AS jsonb), true), updated_at = clock_timestamp()
            WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                AND lease_until > clock_timestamp()
            """, nativeQuery = true)
    int saveDeviceResult(
            @Param("id") UUID id,
            @Param("leaseToken") UUID leaseToken,
            @Param("deviceId") String deviceId,
            @Param("result") String result
    );

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE push_deliveries SET status = 'COMPLETED', completed_at = clock_timestamp(),
                lease_token = NULL, lease_until = NULL, updated_at = clock_timestamp()
            WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                AND lease_until > clock_timestamp()
            """, nativeQuery = true)
    int complete(@Param("id") UUID id, @Param("leaseToken") UUID leaseToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            UPDATE push_deliveries SET status = 'PENDING', lease_token = NULL,
                lease_until = NULL, updated_at = clock_timestamp()
            WHERE id = :id AND status = 'PROCESSING' AND lease_token = :leaseToken
                AND lease_until > clock_timestamp()
            """, nativeQuery = true)
    int release(@Param("id") UUID id, @Param("leaseToken") UUID leaseToken);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM push_deliveries delivery
            WHERE delivery.created_at < :createdBefore
                AND (delivery.lease_until IS NULL OR delivery.lease_until <= clock_timestamp())
                AND EXISTS (
                    SELECT 1 FROM users member
                    WHERE member.id = delivery.user_id AND member.deleted_at IS NULL
                )
            """, nativeQuery = true)
    int deleteExpiredForActiveUsers(@Param("createdBefore") Instant createdBefore);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM push_deliveries delivery
            WHERE (delivery.lease_until IS NULL OR delivery.lease_until <= clock_timestamp())
                AND EXISTS (
                    SELECT 1 FROM users member
                    WHERE member.id = delivery.user_id AND member.deleted_at < :withdrawnBefore
                )
            """, nativeQuery = true)
    int deleteExpiredForWithdrawnUsers(@Param("withdrawnBefore") Instant withdrawnBefore);
}
