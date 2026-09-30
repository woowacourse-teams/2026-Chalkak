package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PushDeviceRepositoryImpl implements PushDeviceRepository {

    private final PushDeviceJpaRepository pushDeviceJpaRepository;

    @Override
    public void lockToken(String tokenHash) {
        pushDeviceJpaRepository.lockToken(tokenHash);
    }

    @Override
    public Optional<UUID> findActiveSessionIdByTokenHash(String tokenHash) {
        return pushDeviceJpaRepository.findActiveSessionIdByTokenHash(tokenHash);
    }

    @Override
    public Optional<PushDevice> findBySessionId(UUID sessionId) {
        return pushDeviceJpaRepository.findBySessionId(sessionId);
    }

    @Override
    public void disableOtherSessionByTokenHash(
            String tokenHash,
            UUID sessionId,
            Instant disabledAt
    ) {
        pushDeviceJpaRepository.disableOtherSessionByTokenHash(tokenHash, sessionId, disabledAt);
    }

    @Override
    public void save(PushDevice device) {
        pushDeviceJpaRepository.saveAndFlush(device);
    }
}
