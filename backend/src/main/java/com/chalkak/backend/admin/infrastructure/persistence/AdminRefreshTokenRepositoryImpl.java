package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminRefreshToken;
import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminRefreshTokenRepositoryImpl implements AdminRefreshTokenRepository {

    private final AdminRefreshTokenJpaRepository adminRefreshTokenJpaRepository;

    @Override
    public Optional<AdminRefreshToken> findByTokenHashForUpdate(String tokenHash) {
        return adminRefreshTokenJpaRepository.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public AdminRefreshToken save(AdminRefreshToken refreshToken) {
        return adminRefreshTokenJpaRepository.save(refreshToken);
    }

    @Override
    public int revokeSession(UUID sessionId, Instant revokedAt) {
        return adminRefreshTokenJpaRepository.revokeSession(sessionId, revokedAt);
    }

    @Override
    public int revokeAllByOwnerId(UUID ownerId, Instant revokedAt) {
        return adminRefreshTokenJpaRepository.revokeAllByOwnerId(ownerId, revokedAt);
    }

    @Override
    public int deleteUnusableBefore(Instant now, Instant revokedThreshold) {
        return adminRefreshTokenJpaRepository.deleteUnusableBefore(now, revokedThreshold);
    }
}
