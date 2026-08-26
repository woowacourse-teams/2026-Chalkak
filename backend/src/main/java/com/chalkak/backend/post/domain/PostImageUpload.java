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

    private static final Map<String, String> REJECTION_MESSAGES = Map.of(
            "UNSUPPORTED_FORMAT", "WebP 이미지만 업로드할 수 있습니다.",
            "CORRUPTED_IMAGE", "사진 파일이 손상되었습니다. 다시 업로드해 주세요.",
            "ANIMATED_IMAGE", "움직이는 이미지는 업로드할 수 없습니다.",
            "TOO_LARGE", "더 작은 용량의 이미지를 업로드해 주세요.",
            "TOO_MANY_PIXELS", "해상도가 너무 큰 이미지입니다. 크기를 줄여 다시 업로드해 주세요.",
            "MISSING_OBJECT", "업로드한 사진을 찾을 수 없습니다. 다시 업로드해 주세요.",
            "PROCESSING_ERROR", "사진을 처리하지 못했습니다. 다시 업로드해 주세요."
    );
    private static final String DEFAULT_REJECTION_MESSAGE =
            "처리할 수 없는 사진입니다. 다시 업로드해 주세요.";

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

    /**
     * 중복·역순 콜백은 상태를 바꾸지 않는다. SQS가 같은 메시지를 다시 전달할 수 있고, 처리 실패 뒤에 도착한
     * 완료 콜백이 거절된 이미지를 되살리면 안 되기 때문이다.
     */
    public void completeProcessing(Map<String, Object> imageMetadata) {
        if (status != PostImageUploadStatus.ISSUED) {
            return;
        }
        this.status = PostImageUploadStatus.READY;
        this.imageMetadata = imageMetadata;
    }

    public void failProcessing(String rejectionReason) {
        if (status != PostImageUploadStatus.ISSUED) {
            return;
        }
        this.status = PostImageUploadStatus.REJECTED;
        this.rejectionReason = rejectionReason;
    }

    public void claim(Instant claimedAt) {
        if (this.claimedAt != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "이미 사용된 사진입니다.");
        }
        if (status == PostImageUploadStatus.REJECTED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    REJECTION_MESSAGES.getOrDefault(rejectionReason, DEFAULT_REJECTION_MESSAGE)
            );
        }
        if (!claimedAt.isBefore(expiresAt)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "사진 업로드 유효 시간이 지났습니다."
            );
        }
        this.claimedAt = claimedAt;
    }

    public boolean isProcessed() {
        return status == PostImageUploadStatus.READY;
    }

    public boolean isRejected() {
        return status == PostImageUploadStatus.REJECTED;
    }

    public boolean isOwnedBy(UUID userId) {
        return user.getId().equals(userId);
    }
}
