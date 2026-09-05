package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.domain.AdminRefreshToken;
import com.chalkak.backend.admin.repository.AdminRefreshTokenRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.RefreshTokenPolicy;
import com.chalkak.backend.auth.service.AccessTokenIssuer;
import com.chalkak.backend.auth.service.RefreshTokenGenerator;
import com.chalkak.backend.auth.service.RefreshTokenHasher;
import com.chalkak.backend.auth.service.RefreshTokenService;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 관리자 리프레시 토큰의 발급과 회전을 담당한다. 절차는 회원과 공유하고, 여기서는 관리자 엔티티와
 * 관리자 scope 액세스 토큰만 채워 넣는다.
 *
 * <p>수명 정책도 회원과 같은 값을 쓴다. 관리자에게 더 짧은 수명을 주는 편이 안전하지만, 지금
 * 나누면 설정만 늘고 실제로 지켜지는 값은 하나뿐이라 회원 쪽 정책을 그대로 따른다.
 */
@Service
public class AdminRefreshTokenService
        extends RefreshTokenService<Admin, AdminRefreshToken> {

    private final AccessTokenIssuer accessTokenIssuer;

    public AdminRefreshTokenService(
            AdminRefreshTokenRepository adminRefreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            RefreshTokenHasher refreshTokenHasher,
            RefreshTokenPolicy refreshTokenPolicy,
            AccessTokenIssuer accessTokenIssuer,
            Clock clock
    ) {
        super(
                adminRefreshTokenRepository,
                refreshTokenGenerator,
                refreshTokenHasher,
                refreshTokenPolicy,
                clock);
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Override
    protected AdminRefreshToken createToken(
            Admin admin,
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        return AdminRefreshToken.create(
                admin,
                sessionId,
                tokenHash,
                expiresAt,
                absoluteExpiresAt);
    }

    @Override
    protected AdminRefreshToken createSuccessor(
            AdminRefreshToken consumed,
            String tokenHash,
            Instant expiresAt
    ) {
        return consumed.createSuccessor(tokenHash, expiresAt);
    }

    @Override
    protected IssuedAccessToken issueAccessToken(AdminRefreshToken consumed) {
        return accessTokenIssuer.issue(
                consumed.getAdmin().getId(),
                AccessTokenScope.ADMIN);
    }
}
