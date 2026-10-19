package com.chalkak.backend.admin.service.banner;

import com.chalkak.backend.banner.domain.Banner;
import com.chalkak.backend.banner.repository.BannerRepository;
import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditHash;
import com.chalkak.backend.admin.domain.AdminAuditSnapshot;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.service.audit.AdminAuditLogCommand;
import com.chalkak.backend.admin.service.audit.AdminAuditLogService;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminBannerService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BannerRepository bannerRepository;
    private final AdminAuditLogService auditLogService;

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
        // URL·문구 원문의 노출을 막고, 문구가 감사 민감값 검증에 걸려 등록이 실패하지 않도록 해시만 남긴다.
        AdminAuditSnapshot afterState = AdminAuditSnapshot.from(Map.of(
                "titleSha256", AdminAuditHash.createSha256(saved.getTitle()),
                "subtitleSha256", AdminAuditHash.createSha256(saved.getSubtitle())));
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
