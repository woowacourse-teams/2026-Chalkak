package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;

/**
 * 가입이 끝나지 않은 Apple 신규 회원의 Refresh Token을 신원별로 임시 보관한다.
 *
 * <p>업로드 식별자가 아니라 신원으로 보관하는 것은, 같은 사용자가 다시 로그인해도 이미 교환한
 * Refresh Token을 그대로 재사용하기 위해서다. Apple 폐기는 토큰 단위라 덮어쓴 토큰은 다시
 * 폐기할 수 없으므로, 한 신원에 여러 행이 남을 수 있고 각 행은 폐기될 때까지 유지한다.
 */
@Entity
@Table(name = "pending_apple_authorizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingAppleAuthorization {

    private static final int ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH = 4096;
    private static final Pattern SUBJECT_HMAC_PATTERN =
            Pattern.compile("^[0-9a-f]{64}$");

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(
            name = "subject_hmac",
            nullable = false,
            updatable = false,
            length = 64)
    private String subjectHmac;

    @Column(
            name = "encrypted_refresh_token",
            nullable = false,
            updatable = false,
            length = ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH)
    private String encryptedRefreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static PendingAppleAuthorization create(
            String subjectHmac,
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        validate(subjectHmac, encryptedRefreshToken, expiresAt);

        PendingAppleAuthorization authorization =
                new PendingAppleAuthorization();
        authorization.subjectHmac = subjectHmac;
        authorization.encryptedRefreshToken = encryptedRefreshToken;
        authorization.expiresAt = expiresAt;
        return authorization;
    }

    /**
     * 만료를 앞으로만 민다. 로그인 재사용은 재로그인 시점 기준으로, 업로드 URL 발급은
     * 회원가입 토큰 만료에 맞춰 각각 갱신을 요청하는데, 이미 더 뒤인 만료를 당기면 가입을
     * 끝낼 시간이 줄어든다.
     */
    public void extendTo(Instant candidate) {
        if (candidate.isAfter(expiresAt)) {
            expiresAt = candidate;
        }
    }

    private static void validate(
            String subjectHmac,
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        if (subjectHmac == null
                || !SUBJECT_HMAC_PATTERN.matcher(subjectHmac).matches()
                || encryptedRefreshToken == null
                || encryptedRefreshToken.isBlank()
                || encryptedRefreshToken.length()
                > ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH
                || expiresAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "임시 Apple 인증 정보가 올바르지 않습니다.");
        }
    }
}
