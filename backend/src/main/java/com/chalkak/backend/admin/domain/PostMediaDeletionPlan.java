package com.chalkak.backend.admin.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "post_media_deletion_plans")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostMediaDeletionPlan {

    private static final int MAX_STORAGE_KEY_LENGTH = 1024;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false, unique = true)
    private UUID postId;

    @Column(name = "post_image_upload_id", updatable = false)
    private UUID postImageUploadId;

    @Column(name = "staging_storage_key", length = MAX_STORAGE_KEY_LENGTH, updatable = false)
    private String stagingStorageKey;

    @Column(
            name = "original_storage_key",
            nullable = false,
            length = MAX_STORAGE_KEY_LENGTH,
            updatable = false
    )
    private String originalStorageKey;

    @Column(name = "thumbnail_storage_key", length = MAX_STORAGE_KEY_LENGTH, updatable = false)
    private String thumbnailStorageKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(
            name = "status",
            nullable = false,
            columnDefinition = "post_media_deletion_status"
    )
    private PostMediaDeletionStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static PostMediaDeletionPlan create(
            UUID postId,
            UUID postImageUploadId,
            String stagingStorageKey,
            String originalStorageKey,
            String thumbnailStorageKey,
            Instant requestedAt
    ) {
        validateRequiredInformation(postId, originalStorageKey, requestedAt);
        validateOptionalStorageKey(stagingStorageKey);
        validateOptionalStorageKey(thumbnailStorageKey);

        PostMediaDeletionPlan plan = new PostMediaDeletionPlan();
        plan.postId = postId;
        plan.postImageUploadId = postImageUploadId;
        plan.stagingStorageKey = stagingStorageKey;
        plan.originalStorageKey = originalStorageKey;
        plan.thumbnailStorageKey = thumbnailStorageKey;
        plan.status = PostMediaDeletionStatus.PENDING;
        plan.attemptCount = 0;
        plan.nextAttemptAt = requestedAt;
        return plan;
    }

    public boolean isSucceeded() {
        return status == PostMediaDeletionStatus.SUCCEEDED;
    }

    public boolean isDue(Instant now) {
        return now != null
                && nextAttemptAt != null
                && !nextAttemptAt.isAfter(now);
    }

    public List<String> storageKeys() {
        return Stream.of(
                        stagingStorageKey,
                        originalStorageKey,
                        thumbnailStorageKey
                )
                .filter(key -> key != null)
                .distinct()
                .toList();
    }

    private static void validateRequiredInformation(
            UUID postId,
            String originalStorageKey,
            Instant requestedAt
    ) {
        if (postId == null || requestedAt == null) {
            throw invalidPlanException();
        }
        validateStorageKey(originalStorageKey);
    }

    private static void validateOptionalStorageKey(String storageKey) {
        if (storageKey == null) {
            return;
        }
        validateStorageKey(storageKey);
    }

    private static void validateStorageKey(String storageKey) {
        if (storageKey == null
                || storageKey.isBlank()
                || storageKey.length() > MAX_STORAGE_KEY_LENGTH) {
            throw invalidPlanException();
        }
    }

    private static BusinessException invalidPlanException() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "게시물 미디어 삭제 계획이 올바르지 않습니다."
        );
    }
}
