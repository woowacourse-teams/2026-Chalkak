package com.chalkak.backend.admin.domain;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "admins")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Admin {

    private static final int MAX_USERNAME_LENGTH = 100;
    private static final int MAX_PASSWORD_HASH_LENGTH = 255;
    private static final String INVALID_ACCOUNT_MESSAGE = "관리자 계정 정보가 올바르지 않습니다.";
    private static final Pattern BCRYPT_PASSWORD_HASH_PATTERN = Pattern.compile(
            "\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, length = MAX_USERNAME_LENGTH)
    private String username;

    @Column(name = "password", nullable = false, length = MAX_PASSWORD_HASH_LENGTH)
    private String passwordHash;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static Admin create(String username, String passwordHash) {
        validateUsername(username);
        validatePasswordHash(passwordHash);
        Admin admin = new Admin();
        admin.username = username;
        admin.passwordHash = passwordHash;
        return admin;
    }

    private static void validateUsername(String username) {
        if (username == null
                || username.isBlank()
                || username.length() > MAX_USERNAME_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_ACCOUNT_MESSAGE);
        }
    }

    private static void validatePasswordHash(String passwordHash) {
        if (passwordHash == null
                || passwordHash.isBlank()
                || passwordHash.length() > MAX_PASSWORD_HASH_LENGTH
                || !BCRYPT_PASSWORD_HASH_PATTERN.matcher(passwordHash).matches()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_ACCOUNT_MESSAGE);
        }
    }
}
