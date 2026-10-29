package com.chalkak.backend.appversion.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "app_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersion {

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @Generated
    @ColumnDefault("uuidv7()")
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private AppPlatform platform;

    @Embedded
    private MinimumSupportedVersion minimumSupportedVersion;

    @Embedded
    private StoreUrl storeUrl;

    @Version
    @Column(nullable = false)
    private long revision;

    @Column(name = "last_changed_by")
    private UUID lastChangedBy;

    @Column(name = "change_reason", length = MAX_REASON_LENGTH)
    private String changeReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppVersion create(
            AppPlatform platform,
            MinimumSupportedVersion minimumSupportedVersion,
            StoreUrl storeUrl,
            UUID adminId,
            String reason
    ) {
        if (platform == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "플랫폼이 필요합니다.");
        }
        AppVersion setting = new AppVersion();
        setting.platform = platform;
        setting.update(
                minimumSupportedVersion,
                storeUrl,
                adminId,
                reason
        );
        return setting;
    }

    public void update(
            MinimumSupportedVersion minimumSupportedVersion,
            StoreUrl storeUrl,
            UUID adminId,
            String reason
    ) {
        if (minimumSupportedVersion == null || storeUrl == null || adminId == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "앱 버전 설정 정보가 필요합니다.");
        }
        storeUrl.validatePlatform(platform);
        String normalizedReason = normalizeChangeReason(reason);
        this.minimumSupportedVersion = minimumSupportedVersion;
        this.storeUrl = storeUrl;
        this.lastChangedBy = adminId;
        this.changeReason = normalizedReason;
    }

    public static String normalizeChangeReason(String reason) {
        String normalized = reason == null ? "" : reason.strip();
        if (normalized.isBlank()
                || normalized.codePointCount(0, normalized.length()) > MAX_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "변경 사유는 앞뒤 공백을 제외하고 1~500자여야 합니다.");
        }
        return normalized;
    }
}
