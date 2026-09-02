package com.chalkak.backend.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserRefreshTokenRepositoryTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

    @Autowired
    private UserRefreshTokenRepository userRefreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("토큰 해시로 리프레시 토큰을 조회한다")
    void findByTokenHashForUpdate_existingToken_returnsToken() {
        // given
        User user = userRepository.save(createUser());
        String tokenHash = createTokenHash();
        userRefreshTokenRepository.save(createRefreshToken(user, UUID.randomUUID(), tokenHash));
        entityManager.flush();
        entityManager.clear();

        // when
        UserRefreshToken found = userRefreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow();

        // then
        assertThat(found.getTokenHash()).isEqualTo(tokenHash);
        assertThat(found.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    @DisplayName("존재하지 않는 토큰 해시로 조회하면 빈 값을 반환한다")
    void findByTokenHashForUpdate_unknownToken_returnsEmpty() {
        // given
        String unknownTokenHash = createTokenHash();

        // when & then
        assertThat(userRefreshTokenRepository.findByTokenHashForUpdate(unknownTokenHash))
                .isEmpty();
    }

    @Test
    @DisplayName("한 회전 계보만 폐기하고 다른 계보는 그대로 둔다")
    void revokeSession_liveSession_revokesOnlyThatSession() {
        // given
        User user = userRepository.save(createUser());
        UUID targetSessionId = UUID.randomUUID();
        UUID otherSessionId = UUID.randomUUID();
        String rotatedTokenHash = createTokenHash();
        String liveTokenHash = createTokenHash();
        String otherTokenHash = createTokenHash();
        userRefreshTokenRepository.save(createRefreshToken(user, targetSessionId, rotatedTokenHash));
        userRefreshTokenRepository.save(createRefreshToken(user, targetSessionId, liveTokenHash));
        userRefreshTokenRepository.save(createRefreshToken(user, otherSessionId, otherTokenHash));
        entityManager.flush();
        entityManager.clear();

        // when
        int revokedCount = userRefreshTokenRepository.revokeSession(targetSessionId, NOW);

        // then
        entityManager.clear();
        assertThat(revokedCount).isEqualTo(2);
        assertThat(findRevokedAt(rotatedTokenHash)).isEqualTo(NOW);
        assertThat(findRevokedAt(liveTokenHash)).isEqualTo(NOW);
        assertThat(findRevokedAt(otherTokenHash)).isNull();
    }

    @Test
    @DisplayName("이미 폐기된 토큰은 다시 폐기 대상으로 세지 않는다")
    void revokeSession_alreadyRevokedToken_isNotCounted() {
        // given
        User user = userRepository.save(createUser());
        UUID sessionId = UUID.randomUUID();
        UserRefreshToken revoked = createRefreshToken(user, sessionId, createTokenHash());
        revoked.revoke(NOW.minusSeconds(60));
        userRefreshTokenRepository.save(revoked);
        userRefreshTokenRepository.save(createRefreshToken(user, sessionId, createTokenHash()));
        entityManager.flush();
        entityManager.clear();

        // when
        int revokedCount = userRefreshTokenRepository.revokeSession(sessionId, NOW);

        // then
        assertThat(revokedCount).isEqualTo(1);
    }

    @Test
    @DisplayName("한 회원의 모든 기기 세션을 폐기하고 다른 회원은 그대로 둔다")
    void revokeAllByOwnerId_multipleSessions_revokesOnlyThatOwner() {
        // given
        User user = userRepository.save(createUser());
        User otherUser = userRepository.save(createUser());
        String firstTokenHash = createTokenHash();
        String secondTokenHash = createTokenHash();
        String otherUserTokenHash = createTokenHash();
        userRefreshTokenRepository.save(
                createRefreshToken(user, UUID.randomUUID(), firstTokenHash));
        userRefreshTokenRepository.save(
                createRefreshToken(user, UUID.randomUUID(), secondTokenHash));
        userRefreshTokenRepository.save(
                createRefreshToken(otherUser, UUID.randomUUID(), otherUserTokenHash));
        entityManager.flush();
        entityManager.clear();

        // when
        int revokedCount = userRefreshTokenRepository.revokeAllByOwnerId(user.getId(), NOW);

        // then
        entityManager.clear();
        assertThat(revokedCount).isEqualTo(2);
        assertThat(findRevokedAt(firstTokenHash)).isEqualTo(NOW);
        assertThat(findRevokedAt(secondTokenHash)).isEqualTo(NOW);
        assertThat(findRevokedAt(otherUserTokenHash)).isNull();
    }

    @Test
    @DisplayName("절대 만료가 지난 토큰과 폐기된 지 오래된 토큰만 삭제한다")
    void deleteUnusableBefore_expiredAndLongRevokedTokens_deletesOnlyThose() {
        // given
        User user = userRepository.save(createUser());
        Instant revokedThreshold = NOW.minusSeconds(600);
        String absolutelyExpiredTokenHash = createTokenHash();
        String expiringNowTokenHash = createTokenHash();
        String longRevokedTokenHash = createTokenHash();
        String justRevokedTokenHash = createTokenHash();
        String liveTokenHash = createTokenHash();
        saveRefreshToken(user, absolutelyExpiredTokenHash, NOW.minusSeconds(1), null);
        saveRefreshToken(user, expiringNowTokenHash, NOW, null);
        saveRefreshToken(
                user,
                longRevokedTokenHash,
                NOW.plusSeconds(3600),
                revokedThreshold.minusSeconds(1));
        saveRefreshToken(user, justRevokedTokenHash, NOW.plusSeconds(3600), revokedThreshold);
        saveRefreshToken(user, liveTokenHash, NOW.plusSeconds(3600), null);
        entityManager.flush();
        entityManager.clear();

        // when
        int deletedCount = userRefreshTokenRepository.deleteUnusableBefore(NOW, revokedThreshold);

        // then
        entityManager.clear();
        assertThat(deletedCount).isEqualTo(2);
        assertThat(existsByTokenHash(absolutelyExpiredTokenHash)).isFalse();
        assertThat(existsByTokenHash(longRevokedTokenHash)).isFalse();
        assertThat(existsByTokenHash(expiringNowTokenHash)).isTrue();
        assertThat(existsByTokenHash(justRevokedTokenHash)).isTrue();
        assertThat(existsByTokenHash(liveTokenHash)).isTrue();
    }

    private void saveRefreshToken(
            User user,
            String tokenHash,
            Instant absoluteExpiresAt,
            Instant revokedAt
    ) {
        UserRefreshToken refreshToken = UserRefreshToken.create(
                user,
                UUID.randomUUID(),
                tokenHash,
                absoluteExpiresAt,
                absoluteExpiresAt);
        if (revokedAt != null) {
            refreshToken.revoke(revokedAt);
        }
        userRefreshTokenRepository.save(refreshToken);
    }

    private UserRefreshToken createRefreshToken(User user, UUID sessionId, String tokenHash) {
        return UserRefreshToken.create(
                user,
                sessionId,
                tokenHash,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200));
    }

    private Instant findRevokedAt(String tokenHash) {
        return userRefreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow()
                .getRevokedAt();
    }

    private boolean existsByTokenHash(String tokenHash) {
        return userRefreshTokenRepository.findByTokenHashForUpdate(tokenHash).isPresent();
    }

    private String createTokenHash() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString();
        return User.create(
                null,
                new SignatureStorageKeys(
                        "signatures/original/" + unique,
                        "signatures/thumbnail/" + unique));
    }
}
