package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PushDevice;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository {

    void lockToken(String tokenHash);

    Optional<UUID> findSessionIdByTokenHash(String tokenHash);

    Optional<PushDevice> findBySessionId(UUID sessionId);

    void deleteBySessionId(UUID sessionId);

    void deleteByUserId(UUID userId);

    void deleteOtherSessionByTokenHash(
            String tokenHash,
            UUID sessionId
    );

    void save(PushDevice device);
}
