package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PushDelivery;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PushDeliveryRepository {
    Optional<PushDelivery> createIfAbsent(UUID eventId, UUID userId);

    Optional<PushDelivery> findByEventIdAndUserId(UUID eventId, UUID userId);

    boolean saveDeviceResult(
            UUID id,
            UUID deviceId,
            PushDelivery.DeviceResult result
    );

    boolean complete(UUID id);

    int deleteExpiredForActiveUsers(Instant createdBefore);

    int deleteExpiredForWithdrawnUsers(Instant withdrawnBefore);
}
