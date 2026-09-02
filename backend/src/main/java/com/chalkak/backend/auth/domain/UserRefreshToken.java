package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.domain.User;
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
@Table(name = "user_refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserRefreshToken extends RefreshToken {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    public static UserRefreshToken create(
            User user,
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        UserRefreshToken refreshToken = new UserRefreshToken();
        refreshToken.validateUser(user);
        refreshToken.initialize(sessionId, tokenHash, expiresAt, absoluteExpiresAt);
        refreshToken.user = user;
        return refreshToken;
    }

    /**
     * 회전으로 뒤를 잇는 토큰을 만든다. 같은 기기의 회전 계보이므로 회원과 세션을 그대로 물려받고,
     * 절대 만료도 최초 로그인 시점 값을 유지해 회전으로 로그인이 무한히 연장되지 않게 한다.
     */
    public UserRefreshToken createSuccessor(String tokenHash, Instant expiresAt) {
        return create(
                user,
                getSessionId(),
                tokenHash,
                expiresAt,
                getAbsoluteExpiresAt());
    }

    private void validateUser(User user) {
        if (user == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "리프레시 토큰의 회원 정보가 올바르지 않습니다.");
        }
    }
}
