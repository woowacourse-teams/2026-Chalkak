package com.chalkak.backend.auth.infrastructure.persistence;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRefreshTokenRepositoryImpl implements UserRefreshTokenRepository {

    private final UserRefreshTokenJpaRepository userRefreshTokenJpaRepository;

    @Override
    public Optional<UUID> findSessionIdByTokenHash(String tokenHash) {
        return userRefreshTokenJpaRepository.findSessionIdByTokenHash(tokenHash);
    }

    @Override
    public List<UUID> findLiveSessionIdsByOwnerId(UUID ownerId) {
        return userRefreshTokenJpaRepository.findLiveSessionIdsByOwnerId(ownerId);
    }

    @Override
    public void lockSession(UUID sessionId) {
        userRefreshTokenJpaRepository.lockSession(sessionId);
    }

    @Override
    public Optional<UserRefreshToken> findByTokenHash(String tokenHash) {
        return userRefreshTokenJpaRepository.findByTokenHash(tokenHash);
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

    /**
     * 두 문장으로 나눠 지우고 합계를 돌려준다. 앞 문장이 지운 행은 뒤 문장이 다시 만나지 못하므로
     * 두 조건에 모두 걸리는 행도 한 번만 세어진다.
     */
    @Override
    public int deleteUnusableBefore(Instant now, Instant revokedThreshold) {
        int absolutelyExpiredCount = userRefreshTokenJpaRepository.deleteAbsolutelyExpiredBefore(now);
        int revokedCount = userRefreshTokenJpaRepository.deleteRevokedBefore(revokedThreshold);
        return absolutelyExpiredCount + revokedCount;
    }
}
