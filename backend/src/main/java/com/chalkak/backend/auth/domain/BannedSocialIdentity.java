package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "banned_social_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BannedSocialIdentity {

    private static final int SUBJECT_HMAC_LENGTH = 64;
    private static final Pattern SUBJECT_HMAC_PATTERN =
            Pattern.compile("[0-9a-f]{" + SUBJECT_HMAC_LENGTH + "}");

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(name = "subject_hmac", nullable = false, updatable = false, length = 64)
    private String subjectHmac;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static BannedSocialIdentity create(
            SocialProvider provider,
            String subjectHmac
    ) {
        BannedSocialIdentity bannedIdentity = new BannedSocialIdentity();
        bannedIdentity.validate(provider, subjectHmac);
        bannedIdentity.provider = provider;
        bannedIdentity.subjectHmac = subjectHmac;
        return bannedIdentity;
    }

    private void validate(SocialProvider provider, String subjectHmac) {
        if (provider == null
                || subjectHmac == null
                || !SUBJECT_HMAC_PATTERN.matcher(subjectHmac).matches()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "차단할 소셜 계정 식별 정보가 올바르지 않습니다.");
        }
    }
}
