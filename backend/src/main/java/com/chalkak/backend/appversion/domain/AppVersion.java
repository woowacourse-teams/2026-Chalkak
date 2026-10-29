package com.chalkak.backend.appversion.domain;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    @Column(name = "change_reason", length = 500)
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
            ChangeReason reason
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
            ChangeReason reason
    ) {
        if (minimumSupportedVersion == null || storeUrl == null || adminId == null
                || reason == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "앱 버전 설정 정보가 필요합니다.");
        }
        storeUrl.validatePlatform(platform);
        this.minimumSupportedVersion = minimumSupportedVersion;
        this.storeUrl = storeUrl;
        this.lastChangedBy = adminId;
        this.changeReason = reason.value();
    }

}
