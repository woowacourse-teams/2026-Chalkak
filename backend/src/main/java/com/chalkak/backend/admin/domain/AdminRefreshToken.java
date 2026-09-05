package com.chalkak.backend.admin.domain;

import com.chalkak.backend.auth.domain.RefreshToken;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminRefreshToken extends RefreshToken {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false, updatable = false)
    private Admin admin;

    public static AdminRefreshToken create(
            Admin admin,
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        AdminRefreshToken refreshToken = new AdminRefreshToken();
        refreshToken.validateAdmin(admin);
        refreshToken.initialize(sessionId, tokenHash, expiresAt, absoluteExpiresAt);
        refreshToken.admin = admin;
        return refreshToken;
    }

    /**
     * 회전으로 뒤를 잇는 토큰을 만든다. 같은 기기의 회전 계보이므로 관리자와 세션을 그대로 물려받고,
     * 절대 만료도 최초 로그인 시점 값을 유지해 회전으로 로그인이 무한히 연장되지 않게 한다.
     */
    public AdminRefreshToken createSuccessor(String tokenHash, Instant expiresAt) {
        return create(
                admin,
                getSessionId(),
                tokenHash,
                expiresAt,
                getAbsoluteExpiresAt());
    }

    private void validateAdmin(Admin admin) {
        if (admin == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "리프레시 토큰의 관리자 정보가 올바르지 않습니다.");
        }
    }
}
