package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "apple_authorizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppleAuthorization {

    private static final int CLIENT_ID_MAX_LENGTH = 255;
    private static final int ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH = 4096;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "social_account_id", nullable = false, updatable = false)
    private SocialAccount socialAccount;

    @Column(name = "client_id", nullable = false, updatable = false, length = CLIENT_ID_MAX_LENGTH)
    private String clientId;

    @Column(
            name = "encrypted_refresh_token",
            nullable = false,
            length = ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH)
    private String encryptedRefreshToken;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppleAuthorization create(
            SocialAccount socialAccount,
            String clientId,
            String encryptedRefreshToken
    ) {
        validateSocialAccount(socialAccount);
        validateClientId(clientId);
        validateEncryptedRefreshToken(encryptedRefreshToken);

        AppleAuthorization authorization = new AppleAuthorization();
        authorization.socialAccount = socialAccount;
        authorization.clientId = clientId;
        authorization.encryptedRefreshToken = encryptedRefreshToken;
        return authorization;
    }

    public void updateEncryptedRefreshToken(String encryptedRefreshToken) {
        validateEncryptedRefreshToken(encryptedRefreshToken);
        this.encryptedRefreshToken = encryptedRefreshToken;
    }

    private static void validateSocialAccount(SocialAccount socialAccount) {
        if (socialAccount == null || socialAccount.getProvider() != SocialProvider.APPLE) {
            throw invalidAuthorization();
        }
    }

    private static void validateClientId(String clientId) {
        if (clientId == null
                || clientId.isBlank()
                || clientId.length() > CLIENT_ID_MAX_LENGTH) {
            throw invalidAuthorization();
        }
    }

    private static void validateEncryptedRefreshToken(String encryptedRefreshToken) {
        if (encryptedRefreshToken == null
                || encryptedRefreshToken.isBlank()
                || encryptedRefreshToken.length() > ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH) {
            throw invalidAuthorization();
        }
    }

    private static BusinessException invalidAuthorization() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 인증 정보가 올바르지 않습니다.");
    }
}
