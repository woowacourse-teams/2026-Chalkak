package com.chalkak.backend.auth.repository;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import java.time.Instant;
import java.util.UUID;

public interface UserRefreshTokenRepository extends RefreshTokenRepository<UserRefreshToken> {

    boolean existsUsableBySessionIdAndUserId(
            UUID sessionId,
            UUID userId,
            Instant now
    );
}
