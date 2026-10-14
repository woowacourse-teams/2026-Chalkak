package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PushDevice;
import com.chalkak.backend.notification.repository.PushDeviceRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PushDeviceRepositoryImpl implements PushDeviceRepository {

    private final PushDeviceJpaRepository pushDeviceJpaRepository;

    @Override
    public List<UUID> findIdsByUserId(UUID userId) {
        return pushDeviceJpaRepository.findIdsByUserId(userId);
    }

    @Override
    public Optional<PushDevice> findByIdAndUserId(UUID id, UUID userId) {
        return pushDeviceJpaRepository.findByIdAndUserId(id, userId);
    }

    @Override
    public void deleteByIdAndTokenHash(UUID id, String tokenHash) {
        pushDeviceJpaRepository.deleteByIdAndTokenHash(id, tokenHash);
    }

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
