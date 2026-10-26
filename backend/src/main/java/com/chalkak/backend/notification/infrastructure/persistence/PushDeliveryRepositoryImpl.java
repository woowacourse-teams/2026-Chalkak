package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
@RequiredArgsConstructor
@Transactional(timeout = 5)
public class PushDeliveryRepositoryImpl implements PushDeliveryRepository {
    private final PushDeliveryJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<PushDelivery> claim(
            UUID eventId,
            UUID userId,
            UUID leaseToken,
            Duration lifetime
    ) {
        jpaRepository.createIfAbsent(eventId, userId);
        int claimed = jpaRepository.claim(
                eventId,
                userId,
                leaseToken,
                lifetime.toMillis()
        );
        if (claimed == 0) {
            return Optional.empty();
        }
        return jpaRepository.findByEventIdAndUserId(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true, timeout = 5)
    public Optional<PushDelivery> findByEventIdAndUserId(UUID eventId, UUID userId) {
        return jpaRepository.findByEventIdAndUserId(eventId, userId);
    }

    @Override
    public boolean renew(
            UUID id,
            UUID leaseToken,
            Duration lifetime
    ) {
        return jpaRepository.renew(
                id,
                leaseToken,
                lifetime.toMillis()
        ) == 1;
    }

    @Override
    public boolean saveDeviceResult(
            UUID id,
            UUID leaseToken,
            UUID deviceId,
            PushDelivery.DeviceResult result
    ) {
        return jpaRepository.saveDeviceResult(
                id,
                leaseToken,
                deviceId.toString(),
                objectMapper.writeValueAsString(result)
        ) == 1;
    }

    @Override
    public boolean complete(UUID id, UUID leaseToken) {
        return jpaRepository.complete(id, leaseToken) == 1;
    }

    @Override
    public boolean release(UUID id, UUID leaseToken) {
        return jpaRepository.release(id, leaseToken) == 1;
    }
    @Override
    public int deleteExpiredForActiveUsers(Instant createdBefore) {
        return jpaRepository.deleteExpiredForActiveUsers(createdBefore);
    }

    @Override
    public int deleteExpiredForWithdrawnUsers(Instant withdrawnBefore) {
        return jpaRepository.deleteExpiredForWithdrawnUsers(withdrawnBefore);
    }
}
