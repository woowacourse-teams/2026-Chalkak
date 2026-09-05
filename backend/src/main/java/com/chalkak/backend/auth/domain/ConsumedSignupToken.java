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
import org.springframework.data.domain.Persistable;

/**
 * 회원가입 완료에 성공적으로 쓰인 signupToken(jti)을 기록한다. 같은 jti로 다시 저장을
 * 시도하면 기본키 유니크 제약에 걸려 실패하므로, 저장 성공 여부가 곧 "이 토큰이 이번이
 * 처음 쓰이는 것인지"를 뜻한다. 클라이언트가 id를 직접 지정하는 엔티티라
 * {@link Persistable}을 구현해 Spring Data JPA가 매번 INSERT를 시도하게 한다. 이걸
 * 구현하지 않으면 id가 이미 채워져 있다고 보고 merge(조회 후 있으면 갱신)로 동작해,
 * 같은 jti가 이미 있어도 예외 없이 조용히 덮어써 재사용을 막지 못한다.
 */
@Entity
@Table(name = "consumed_signup_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsumedSignupToken implements Persistable<String> {

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

    @Override
    public String getId() {
        return jti;
    }

    @Override
    public boolean isNew() {
        return true;
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
