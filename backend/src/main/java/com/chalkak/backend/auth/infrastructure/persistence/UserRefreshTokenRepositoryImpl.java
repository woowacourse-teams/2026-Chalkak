package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRefreshTokenRepositoryImpl implements UserRefreshTokenRepository {

    private final UserRefreshTokenJpaRepository userRefreshTokenJpaRepository;

    @Override
    public Optional<UserRefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return userRefreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public UserRefreshToken save(UserRefreshToken refreshToken) {
        return userRefreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public int revokeSession(UUID sessionId, Instant revokedAt) {
        return userRefreshTokenJpaRepository.revokeSession(sessionId, revokedAt);
    }

    @Override
    public int revokeAllByOwnerId(UUID ownerId, Instant revokedAt) {
        return userRefreshTokenJpaRepository.revokeAllByOwnerId(ownerId, revokedAt);
    }

    @Override
    public int deleteUnusableBefore(Instant now, Instant revokedThreshold) {
        return userRefreshTokenJpaRepository.deleteUnusableBefore(now, revokedThreshold);
    }
}
