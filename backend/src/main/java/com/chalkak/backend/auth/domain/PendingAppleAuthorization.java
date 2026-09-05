package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "pending_apple_authorizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingAppleAuthorization implements Persistable<UUID> {

    private static final int ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH = 4096;

    @Id
    @Column(name = "upload_id", nullable = false, updatable = false)
    private UUID uploadId;

    @Column(
            name = "encrypted_refresh_token",
            nullable = false,
            updatable = false,
            length = ENCRYPTED_REFRESH_TOKEN_MAX_LENGTH)
    private String encryptedRefreshToken;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    public static PendingAppleAuthorization create(
            UUID uploadId,
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        validate(uploadId, encryptedRefreshToken, expiresAt);

        PendingAppleAuthorization authorization =
                new PendingAppleAuthorization();
        authorization.uploadId = uploadId;
        authorization.encryptedRefreshToken = encryptedRefreshToken;
        authorization.expiresAt = expiresAt;
        return authorization;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @Override
    public UUID getId() {
        return uploadId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    private static void validate(
            UUID uploadId,
            String encryptedRefreshToken,
            Instant expiresAt
    ) {
        if (uploadId == null
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
