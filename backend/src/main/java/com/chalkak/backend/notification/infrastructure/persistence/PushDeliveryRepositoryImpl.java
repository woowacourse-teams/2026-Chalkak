package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDelivery;
import com.chalkak.backend.notification.repository.PushDeliveryRepository;
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
    public Optional<PushDelivery> createIfAbsent(UUID eventId, UUID userId) {
        jpaRepository.createIfAbsent(eventId, userId);
        return jpaRepository.findByEventIdAndUserId(eventId, userId);
    }

    @Override
    @Transactional(readOnly = true, timeout = 5)
    public Optional<PushDelivery> findByEventIdAndUserId(UUID eventId, UUID userId) {
        return jpaRepository.findByEventIdAndUserId(eventId, userId);
    }

    @Override
    public boolean saveDeviceResult(
            UUID id,
            UUID deviceId,
            PushDelivery.DeviceResult result
    ) {
        return jpaRepository.saveDeviceResult(
                id,
                deviceId.toString(),
                objectMapper.writeValueAsString(result)
        ) == 1;
    }

    @Override
    public boolean complete(UUID id) {
        return jpaRepository.complete(id) == 1;
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
