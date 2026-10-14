package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PushDevice;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface PushDeviceRepository {

    List<UUID> findIdsByUserId(UUID userId);

    Optional<PushDevice> findByIdAndUserId(UUID id, UUID userId);

    void deleteByIdAndTokenHash(UUID id, String tokenHash);

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
