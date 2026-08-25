package com.chalkak.backend.post.domain;

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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * 게시물 이미지 업로드 권한. presigned URL을 발급하는 시점에 만들어지므로 Lambda 완료 콜백과 게시물 생성 요청 중
 * 어느 쪽이 먼저 도착해도 상대가 남긴 상태를 읽을 수 있다.
 */
@Entity
@Table(name = "post_image_uploads")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostImageUpload {

    public static final Duration CLAIM_TTL = Duration.ofHours(1);

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "post_image_upload_status")
    private PostImageUploadStatus status;

    @Column(name = "rejection_reason", length = 50)
    private String rejectionReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_metadata", columnDefinition = "jsonb")
    private Map<String, Object> imageMetadata;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private PostImageUpload(User user, Instant issuedAt) {
        this.user = user;
        this.status = PostImageUploadStatus.ISSUED;
        this.expiresAt = issuedAt.plus(CLAIM_TTL);
    }

    public static PostImageUpload createPostImageUpload(User user, Instant issuedAt) {
        if (user == null || issuedAt == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "사진 업로드 발급 정보가 올바르지 않습니다."
            );
        }
        return new PostImageUpload(user, issuedAt);
    }
}
