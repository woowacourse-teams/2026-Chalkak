package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PushDevice;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository {

    void lockToken(String tokenHash);

    Optional<UUID> findActiveSessionIdByTokenHash(String tokenHash);

    Optional<PushDevice> findBySessionId(UUID sessionId);

    void disableBySessionId(UUID sessionId, Instant disabledAt);

    void disableOtherSessionByTokenHash(
            String tokenHash,
            UUID sessionId,
            Instant disabledAt
    );

    void save(PushDevice device);
}
