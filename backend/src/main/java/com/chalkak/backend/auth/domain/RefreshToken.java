package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Duration;
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
 * 리프레시 토큰의 공통 상태와 수명 판정을 담는다. 회원과 관리자는 각자 실제 FK를 유지하려고 테이블을
 * 나눴기 때문에, 두 엔티티가 같은 규칙을 두 번 구현하지 않도록 여기에 모아 둔다.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class RefreshToken {

    private static final Pattern TOKEN_HASH_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final String INVALID_TOKEN_MESSAGE = "리프레시 토큰 정보가 올바르지 않습니다.";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "absolute_expires_at", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    /**
     * 이미 회전된 토큰이 다시 들어왔는지 판단한다. 회전 직후 유예 시간 안의 재요청은 응답을 받지 못한
     * 클라이언트의 재시도로 보고 탈취로 취급하지 않는다.
     */
    public boolean isReused(Instant now, Duration reuseGrace) {
        if (rotatedAt == null) {
            return false;
        }
        return !rotatedAt.isAfter(now.minus(reuseGrace));
    }

    /**
     * 비활동 만료와 절대 만료 중 하나라도 지났으면 만료로 본다. 절대 만료는 최초 로그인 시점에 고정되며
     * 회전으로 연장되지 않는다.
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    /**
     * 회전 시각을 기록한다. 같은 토큰이 두 번 회전돼도 최초 회전 시각을 유지해야 유예 구간 계산이
     * 흔들리지 않으므로 멱등하게 동작한다.
     */
    public void rotate(Instant now) {
        if (rotatedAt != null) {
            return;
        }
        this.rotatedAt = now;
    }

    /**
     * 토큰을 폐기한다. lineage 단위 폐기가 이미 폐기된 토큰을 다시 훑을 수 있으므로 멱등하게 둔다.
     */
    public void revoke(Instant now) {
        if (revokedAt != null) {
            return;
        }
        this.revokedAt = now;
    }

    protected void initialize(
            UUID sessionId,
            String tokenHash,
            Instant expiresAt,
            Instant absoluteExpiresAt
    ) {
        validateSessionId(sessionId);
        validateTokenHash(tokenHash);
        validateExpiry(expiresAt, absoluteExpiresAt);
        this.sessionId = sessionId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.absoluteExpiresAt = absoluteExpiresAt;
    }

    private void validateSessionId(UUID sessionId) {
        if (sessionId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_TOKEN_MESSAGE);
        }
    }

    private void validateTokenHash(String tokenHash) {
        if (tokenHash == null || !TOKEN_HASH_PATTERN.matcher(tokenHash).matches()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_TOKEN_MESSAGE);
        }
    }

    private void validateExpiry(Instant expiresAt, Instant absoluteExpiresAt) {
        if (expiresAt == null || absoluteExpiresAt == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_TOKEN_MESSAGE);
        }
        if (expiresAt.isAfter(absoluteExpiresAt)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, INVALID_TOKEN_MESSAGE);
        }
    }
}
