package com.chalkak.backend.admin.service.banner;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditHash;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBannerService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_DELETION_REASON_LENGTH = 500;

    private final BannerRepository bannerRepository;
    private final AdminAuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public AdminBannerResult createBanner(
            UUID adminId,
            String title,
            String subtitle,
            String webViewUrl,
            String thumbnailUrl
    ) {
        Banner banner = Banner.create(title, subtitle, webViewUrl, thumbnailUrl);
        validateDuplicateWebView(banner.getWebViewUrl());
        Banner saved = bannerRepository.save(banner);
        AdminAuditSnapshot afterState = createBannerState(saved);
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.BANNER_CREATED, AdminTargetType.BANNER, saved.getId(), null,
                AdminAuditSnapshot.from(Map.of()), afterState));
        return AdminBannerResult.from(saved);
    }

    @Transactional(readOnly = true)
    public AdminBannerListResult getBanners(int page, int pageSize) {
        validatePagination(page, pageSize);
        return AdminBannerListResult.from(bannerRepository.findBanners(page, pageSize));
    }

    @Transactional
    public void deleteBanner(
            UUID bannerId,
            UUID adminId,
            String reason
    ) {
        String normalizedReason = normalizeDeletionReason(reason);
        Banner banner = bannerRepository.findByIdForUpdate(bannerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.BUSINESS_ERROR,
                        "배너를 찾을 수 없습니다."));
        if (banner.isDeleted()) {
            return;
        }
        AdminAuditSnapshot beforeState = createDeletionState(banner);
        banner.delete(clock.instant());
        auditLogService.createAuditLog(new AdminAuditLogCommand(
                adminId, AdminAction.BANNER_DELETED, AdminTargetType.BANNER, banner.getId(),
                normalizedReason, beforeState, createDeletionState(banner)));
    }

    private String normalizeDeletionReason(String reason) {
        String normalized = reason == null ? "" : reason.trim().strip();
        if (normalized.isBlank()
                || normalized.codePointCount(0, normalized.length()) > MAX_DELETION_REASON_LENGTH) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "배너 삭제 사유는 1자 이상 500자 이하여야 합니다.");
        }
        return normalized;
    }

    // URL·문구 원문의 노출을 막고, 문구가 감사 민감값 검증에 걸려 등록이 실패하지 않도록 해시만 남긴다.
    private AdminAuditSnapshot createBannerState(Banner banner) {
        return AdminAuditSnapshot.from(Map.of(
                "titleSha256", AdminAuditHash.createSha256(banner.getTitle()),
                "subtitleSha256", AdminAuditHash.createSha256(banner.getSubtitle())));
    }

    private AdminAuditSnapshot createDeletionState(Banner banner) {
        Map<String, Object> state = new LinkedHashMap<>(createBannerState(banner).values());
        state.put("deletedAt", banner.getDeletedAt());
        return AdminAuditSnapshot.from(state);
    }

    private void validatePagination(int page, int pageSize) {
        long offset = ((long) page - 1) * pageSize;
        if (page < 1 || pageSize < 1 || pageSize > MAX_PAGE_SIZE
                || offset > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "조회 조건이 올바르지 않습니다.");
        }
    }

    private void validateDuplicateWebView(String webViewUrl) {
        if (bannerRepository.existsByWebViewUrl(webViewUrl)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "동일한 웹뷰 URL의 배너가 이미 존재합니다.");
        }
    }

}
