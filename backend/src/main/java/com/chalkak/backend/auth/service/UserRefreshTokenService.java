package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.RefreshTokenPolicy;
import com.chalkak.backend.auth.domain.UserRefreshToken;
import com.chalkak.backend.auth.repository.UserRefreshTokenRepository;
import com.chalkak.backend.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 회원 리프레시 토큰의 발급과 회전을 담당한다. 절차는 관리자와 공유하고, 여기서는 회원 엔티티와
 * 회원 scope 액세스 토큰만 채워 넣는다.
 *
 * <p>재발급 경로에서 회원 상태는 조회하지 않는다. 탈퇴는 탈퇴 처리에서 계보를 직접 폐기하고 차단은
 * 요청마다 {@code UsableUserPolicy}가 막으므로, 여기에 회원 조회를 추가하면 같은 판정을 두 곳에서
 * 하게 된다.
 */
@Service
public class UserRefreshTokenService
        extends RefreshTokenService<User, UserRefreshToken> {

    private final AccessTokenIssuer accessTokenIssuer;

    public UserRefreshTokenService(
            UserRefreshTokenRepository userRefreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenPolicy refreshTokenPolicy,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock
    ) {
        super(
                userRefreshTokenRepository,
                refreshTokenGenerator,
                refreshTokenHasher,
                refreshTokenPolicy,
                clock);
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Override
    protected UserRefreshToken createToken(
            User user,
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        return UserRefreshToken.create(
                user,
                sessionId,
                tokenHash,
                expiresAt,
                absoluteExpiresAt);
    }

    @Override
    protected UserRefreshToken createSuccessor(
            UserRefreshToken consumed,
            String tokenHash,
            Instant expiresAt
    ) {
        return consumed.createSuccessor(tokenHash, expiresAt);
    }

    @Override
    protected IssuedAccessToken issueAccessToken(UserRefreshToken consumed) {
        return accessTokenIssuer.issue(consumed.getUser().getId());
    }
}
