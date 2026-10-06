package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
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
    public Optional<UUID> findSessionIdByTokenHash(String tokenHash) {
        return pushDeviceJpaRepository.findSessionIdByTokenHash(tokenHash);
    }

    @Override
    public Optional<PushDevice> findBySessionId(UUID sessionId) {
        return pushDeviceJpaRepository.findBySessionId(sessionId);
    }

    @Override
    public void deleteBySessionId(UUID sessionId) {
        pushDeviceJpaRepository.deleteBySessionId(sessionId);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        pushDeviceJpaRepository.deleteByUserId(userId);
    }

    @Override
    public void deleteOtherSessionByTokenHash(
            String tokenHash,
            UUID sessionId
    ) {
        pushDeviceJpaRepository.deleteOtherSessionByTokenHash(tokenHash, sessionId);
    }

    @Override
    public void save(PushDevice device) {
        pushDeviceJpaRepository.saveAndFlush(device);
    }
}
