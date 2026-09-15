package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원가입 완료에 성공적으로 쓰인 signupToken(jti)을 기록한다. jti가 기본키라 한 토큰은 한 번만 기록되고, 기록에
 * 성공했는지가 곧 "이 토큰이 이번이 처음 쓰이는 것인지"를 뜻한다.
 */
@Entity
@Table(name = "consumed_signup_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedSignupToken {

    private static final int JTI_MAX_LENGTH = 36;

    @Id
    @Column(name = "jti", nullable = false, updatable = false, length = JTI_MAX_LENGTH)
    private String jti;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    public static ConsumedSignupToken create(String jti, Instant expiresAt) {
        validateJti(jti);
        validateExpiresAt(expiresAt);

        ConsumedSignupToken token = new ConsumedSignupToken();
        token.jti = jti;
        token.expiresAt = expiresAt;
        return token;
    }

    private static void validateJti(String jti) {
        if (jti == null || jti.isBlank() || jti.length() > JTI_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "회원가입 토큰 식별자가 올바르지 않습니다.");
        }
    }

    private static void validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "회원가입 토큰 만료 시각이 올바르지 않습니다.");
        }
    }
}
