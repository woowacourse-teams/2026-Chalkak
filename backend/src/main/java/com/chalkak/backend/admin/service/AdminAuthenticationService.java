package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
import com.chalkak.backend.auth.domain.IssuedRefreshToken;
import com.chalkak.backend.auth.service.AccessTokenIssuer;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.UnauthorizedException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthenticationService {

    static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final String AUTHENTICATION_FAILURE_MESSAGE =
            "아이디 또는 비밀번호가 올바르지 않습니다.";

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AdminRefreshTokenService adminRefreshTokenService;

    /**
     * 로그인 성공은 리프레시 토큰 계보를 새로 만들면서 행을 남기므로, 클래스에 걸린 읽기 전용
     * 트랜잭션으로는 처리할 수 없다. 인증 실패 경로는 여전히 읽기만 하지만 한 트랜잭션을 공유하는
     * 이상 쓰기를 허용하는 쪽으로 맞춘다. 읽기 전용은 조회 메서드에 그대로 남는다.
     */
    @Transactional
    public AdminLoginResult login(String username, String password) {
        Optional<Admin> foundAdmin = adminRepository.findByUsername(username);
        String passwordHash = foundAdmin
                .map(Admin::getPasswordHash)
                .orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(password, passwordHash);

        if (foundAdmin.isEmpty() || !passwordMatches) {
            throw createAuthenticationFailure();
        }

        Admin admin = foundAdmin.orElseThrow();
        IssuedAccessToken accessToken = accessTokenIssuer.issue(
                admin.getId(),
                AccessTokenScope.ADMIN
        );
        IssuedRefreshToken refreshToken = adminRefreshTokenService.issue(admin);
        return new AdminLoginResult(
                admin.getId(),
                admin.getUsername(),
                accessToken,
                refreshToken
        );
    }

    public CurrentAdminResult getCurrentAdmin(UUID adminId) {
        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(this::createAuthenticationFailure);
        return new CurrentAdminResult(admin.getId(), admin.getUsername());
    }

    private UnauthorizedException createAuthenticationFailure() {
        return new UnauthorizedException(
                ErrorCode.UNAUTHORIZED,
                AUTHENTICATION_FAILURE_MESSAGE
        );
    }
}
