package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.domain.Admin;
import com.chalkak.backend.admin.repository.AdminRepository;
import com.chalkak.backend.auth.domain.AccessTokenScope;
import com.chalkak.backend.auth.domain.IssuedAccessToken;
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
        return new AdminLoginResult(
                admin.getId(),
                admin.getUsername(),
                accessToken
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
