package com.chalkak.backend.admin.service.appversion;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import com.chalkak.backend.appversion.domain.ChangeReason;
import com.chalkak.backend.appversion.domain.MinimumSupportedVersion;
import com.chalkak.backend.appversion.domain.StoreUrl;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAppVersionService {

    private final AppVersionRepository repository;
    private final AdminAuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<SettingResult> getAppVersions() {
        return Arrays.stream(AppPlatform.values())
                .map(platform -> repository.findByPlatform(platform)
                        .map(SettingResult::from)
                        .orElseGet(() -> SettingResult.createUnconfigured(platform)))
                .toList();
    }

    @Transactional
    public SettingResult saveAppVersion(
            UUID adminId,
            AppPlatform platform,
            Long revision,
            String minimumSupportedVersion,
            String storeUrl,
            String reason
    ) {
        if (adminId == null || platform == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "관리자와 플랫폼 정보가 필요합니다.");
        }
        MinimumSupportedVersion version = new MinimumSupportedVersion(minimumSupportedVersion);
        StoreUrl url = StoreUrl.from(storeUrl);
        url.validatePlatform(platform);
        ChangeReason changeReason = new ChangeReason(reason);
        repository.lockPlatform(platform);
        AppVersion setting = repository.findByPlatform(platform).orElse(null);
        // 재시도와 같은 값 재저장은 시각·사유·관리자·revision·감사 이력을 모두 유지한다.
        if (setting != null && version.equals(setting.getMinimumSupportedVersion())
                && url.equals(setting.getStoreUrl())) {
            return SettingResult.from(setting);
        }
        Long currentRevision = setting == null ? null : setting.getRevision();
        if (!Objects.equals(revision, currentRevision)) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_CHANGED,
                    "설정이 변경되었습니다. 다시 조회한 뒤 저장해 주세요.");
        }
        if (setting == null) {
            return saveWithAudit(
                    adminId,
                    AppVersion.create(
                            platform,
                            version,
                            url,
                            adminId,
                            changeReason
                    ),
                    AdminAction.APP_VERSION_CREATED,
                    AdminAuditSnapshot.from(Map.of()),
                    changeReason
            );
        }
        AdminAuditSnapshot before = createSnapshot(setting);
        setting.update(
                version,
                url,
                adminId,
                changeReason
        );
        return saveWithAudit(
                adminId,
                setting,
                AdminAction.APP_VERSION_UPDATED,
                before,
                changeReason
        );
    }

    private SettingResult saveWithAudit(
            UUID adminId,
            AppVersion setting,
            AdminAction action,
            AdminAuditSnapshot before,
            ChangeReason reason
    ) {
        AppVersion saved = repository.saveAndFlush(setting);
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId,
                action,
                AdminTargetType.APP_VERSION,
                saved.getId(),
                reason.value(),
                before,
                createSnapshot(saved)
        ));
        return SettingResult.from(saved);
    }

    private AdminAuditSnapshot createSnapshot(AppVersion setting) {
        return AdminAuditSnapshot.fromAppVersion(
                setting.getPlatform(),
                setting.getMinimumSupportedVersion(),
                setting.getStoreUrl(),
                setting.getRevision()
        );
    }

    public record SettingResult(
            AppPlatform platform,
            String minimumSupportedVersion,
            String storeUrl,
            Long revision,
            UUID lastChangedBy,
            String changeReason,
            Instant createdAt,
            Instant updatedAt
    ) {

        public static SettingResult from(AppVersion setting) {
            return new SettingResult(
                    setting.getPlatform(),
                    setting.getMinimumSupportedVersion().value(),
                    setting.getStoreUrl().value(),
                    setting.getRevision(),
                    setting.getLastChangedBy(),
                    setting.getChangeReason(),
                    setting.getCreatedAt(),
                    setting.getUpdatedAt()
            );
        }

        public static SettingResult createUnconfigured(AppPlatform platform) {
            return new SettingResult(
                    platform,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }
    }
}
