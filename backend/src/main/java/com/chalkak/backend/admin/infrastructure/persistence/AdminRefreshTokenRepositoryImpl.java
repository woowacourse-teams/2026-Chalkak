package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminRefreshToken;
import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminRefreshTokenRepositoryImpl implements AdminRefreshTokenRepository {

    private final AdminRefreshTokenJpaRepository adminRefreshTokenJpaRepository;

    @Override
    public Optional<UUID> findSessionIdByTokenHash(String tokenHash) {
        return adminRefreshTokenJpaRepository.findSessionIdByTokenHash(tokenHash);
    }

    @Override
    public List<UUID> findLiveSessionIdsByOwnerId(UUID ownerId) {
        return adminRefreshTokenJpaRepository.findLiveSessionIdsByOwnerId(ownerId);
    }

    @Override
    public void lockSession(UUID sessionId) {
        adminRefreshTokenJpaRepository.lockSession(sessionId);
    }

    @Override
    public Optional<AdminRefreshToken> findByTokenHash(String tokenHash) {
        return adminRefreshTokenJpaRepository.findByTokenHash(tokenHash);
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

    /**
     * 두 문장으로 나눠 지우고 합계를 돌려준다. 앞 문장이 지운 행은 뒤 문장이 다시 만나지 못하므로
     * 두 조건에 모두 걸리는 행도 한 번만 세어진다.
     */
    @Override
    public int deleteUnusableBefore(Instant now, Instant revokedThreshold) {
        int absolutelyExpiredCount = adminRefreshTokenJpaRepository.deleteAbsolutelyExpiredBefore(now);
        int revokedCount = adminRefreshTokenJpaRepository.deleteRevokedBefore(revokedThreshold);
        return absolutelyExpiredCount + revokedCount;
    }
}
