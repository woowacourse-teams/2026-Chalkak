package com.chalkak.backend.user.domain;

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
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String WITHDRAWN_EMAIL_FORMAT = "withdrawn+%s@chalkak.invalid";
    private static final String WITHDRAWN_SIGNATURE_KEY_FORMAT = "withdrawn/%s";

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "signature_original_storage_key", nullable = false, length = 1024)
    private String signatureOriginalStorageKey;

    @Column(name = "signature_thumbnail_storage_key", length = 1024)
    private String signatureThumbnailStorageKey;

    @Column(name = "pending_signature_upload_id")
    private UUID pendingSignatureUploadId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "signature_processing_status")
    private SignatureProcessingStatus signatureProcessingStatus;

    @Column(name = "signature_processing_started_at")
    private Instant signatureProcessingStartedAt;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static User create(String email, SignatureStorageKeys storageKeys) {
        User user = new User();
        user.validateStorageKeys(storageKeys);
        user.email = email;
        user.status = UserStatus.ACTIVE;
        user.signatureOriginalStorageKey = storageKeys.originalStorageKey();
        user.signatureThumbnailStorageKey = storageKeys.thumbnailStorageKey();
        return user;
    }

    public void withdraw() {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "이미 탈퇴한 회원입니다.");
        }
        anonymize();
        delete();
    }

    public void startSignatureProcessing(UUID uploadId, Instant startedAt) {
        if (isDeleted()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "이미 탈퇴한 회원입니다.");
        }
        if (uploadId == null || startedAt == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "사인 이미지 업로드 정보가 올바르지 않습니다.");
        }
        this.pendingSignatureUploadId = uploadId;
        this.signatureProcessingStatus = SignatureProcessingStatus.PROCESSING;
        this.signatureProcessingStartedAt = startedAt;
    }

    public boolean completeSignatureProcessing(UUID uploadId, SignatureStorageKeys storageKeys) {
        if (!isProcessing(uploadId)) {
            return false;
        }
        validateStorageKeys(storageKeys);
        this.signatureOriginalStorageKey = storageKeys.originalStorageKey();
        this.signatureThumbnailStorageKey = storageKeys.thumbnailStorageKey();
        clearSignatureProcessing();
        return true;
    }

    public boolean failSignatureProcessing(UUID uploadId) {
        if (!isProcessing(uploadId)) {
            return false;
        }
        this.signatureProcessingStatus = SignatureProcessingStatus.FAILED;
        return true;
    }

    /**
     * 진행 중이던 사인 처리를 포기한다. 이후 도착하는 그 작업의 콜백은 pending 불일치로 무시된다.
     */
    public void cancelSignatureProcessing() {
        clearSignatureProcessing();
    }

    public boolean hasSignature(String storageKey) {
        return storageKey != null
                && storageKey.equals(signatureOriginalStorageKey);
    }

    public boolean isSignatureProcessing(UUID uploadId) {
        return isProcessing(uploadId);
    }

    public boolean isSignatureProcessingFailed(UUID uploadId) {
        return uploadId != null
                && uploadId.equals(pendingSignatureUploadId)
                && signatureProcessingStatus == SignatureProcessingStatus.FAILED;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 탈퇴하지 않았고 이용이 정지되지도 않아 서비스를 정상적으로 이용할 수 있는 상태인지 판단한다.
     */
    public boolean isActive() {
        return !isDeleted() && status == UserStatus.ACTIVE;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isProcessing(UUID uploadId) {
        return uploadId != null
                && uploadId.equals(pendingSignatureUploadId)
                && signatureProcessingStatus == SignatureProcessingStatus.PROCESSING;
    }

    private void validateStorageKeys(SignatureStorageKeys storageKeys) {
        if (storageKeys == null
                || isBlank(storageKeys.originalStorageKey())
                || isBlank(storageKeys.thumbnailStorageKey())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "사인 이미지 업로드 정보가 올바르지 않습니다.");
        }
    }

    private void clearSignatureProcessing() {
        this.pendingSignatureUploadId = null;
        this.signatureProcessingStatus = null;
        this.signatureProcessingStartedAt = null;
    }

    private void anonymize() {
        this.email = WITHDRAWN_EMAIL_FORMAT.formatted(id);
        this.signatureOriginalStorageKey =
                WITHDRAWN_SIGNATURE_KEY_FORMAT.formatted(id);
        this.signatureThumbnailStorageKey = null;
        clearSignatureProcessing();
    }
}
