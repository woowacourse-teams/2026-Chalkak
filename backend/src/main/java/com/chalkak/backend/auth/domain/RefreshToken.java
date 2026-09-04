package com.chalkak.backend.auth.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
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
     * 이미 회전된 토큰이 다시 들어왔는지 판단한다. 회전된 토큰이 또 제시됐다는 것은 그 값의 사본이
     * 어딘가에 남아 있다는 뜻이므로 예외 없이 탈취로 본다.
     *
     * <p>클라이언트는 재발급 호출을 한 번에 하나만 띄우고 나머지 호출은 그 결과를 기다려야 한다.
     * 다만 이 계약이 모든 재제시를 덮지는 못한다. 재발급 응답이 유실되면 클라이언트는 새 토큰을
     * 받지 못한 채 같은 토큰으로 재시도할 수밖에 없고, 그때도 여기서 탈취로 판정한다. 틀리는
     * 방향이 재로그인이라 탈취된 계보를 살려 두는 것보다 안전하다고 보고 감수한 결과다.
     */
    public boolean isReused() {
        return rotatedAt != null;
    }

    /**
     * 비활동 만료와 절대 만료 중 하나라도 지났으면 만료로 본다. 절대 만료는 최초 로그인 시점에 고정되며
     * 회전으로 연장되지 않는다.
     */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt) || !now.isBefore(absoluteExpiresAt);
    }

    /**
     * 회전 시각을 기록한다. 이 값은 재사용 탐지가 보는 유일한 증거이므로, 최초 회전 시각을 덮어쓰지
     * 않도록 멱등하게 동작한다.
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
