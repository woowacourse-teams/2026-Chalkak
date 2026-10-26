package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PushDelivery;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PushDeliveryRepository {
    Optional<PushDelivery> claim(
            UUID eventId,
            UUID userId,
            UUID leaseToken,
            Duration lifetime
    );

    Optional<PushDelivery> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean renew(
            UUID id,
            UUID leaseToken,
            Duration lifetime
    );

    boolean saveDeviceResult(
            UUID id,
            UUID leaseToken,
            UUID deviceId,
            PushDelivery.DeviceResult result
    );

    boolean complete(UUID id, UUID leaseToken);

    boolean release(UUID id, UUID leaseToken);

    int deleteExpiredForActiveUsers(Instant createdBefore);

    int deleteExpiredForWithdrawnUsers(Instant withdrawnBefore);
}
