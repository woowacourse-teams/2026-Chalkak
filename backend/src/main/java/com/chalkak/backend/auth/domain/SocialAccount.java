package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount {

    private static final int SUBJECT_MAX_LENGTH = 255;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "provider", nullable = false, updatable = false)
    private SocialProvider provider;

    @Column(name = "subject", nullable = false, updatable = false, length = 255)
    private String subject;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public static SocialAccount create(
            User user,
            SocialProvider provider,
            String subject) {
        SocialAccount socialAccount = new SocialAccount();
        socialAccount.validateAssociation(user, provider);
        socialAccount.validateSubject(subject);
        socialAccount.user = user;
        socialAccount.provider = provider;
        socialAccount.subject = subject;
        return socialAccount;
    }

    private void validateAssociation(User user, SocialProvider provider) {
        if (user == null || provider == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "소셜 계정 연결 정보가 올바르지 않습니다.");
        }
    }

    private void validateSubject(String subject) {
        if (subject == null
                || subject.isBlank()
                || subject.length() > SUBJECT_MAX_LENGTH) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "소셜 계정 식별 정보가 올바르지 않습니다.");
        }
    }
}
