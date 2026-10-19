package com.chalkak.backend.admin.banner.service;

import com.chalkak.backend.admin.banner.domain.Banner;
import java.time.Instant;
import java.util.UUID;

public record AdminBannerResult(
        UUID id,
        String title,
        String subtitle,
        String webViewUrl,
        String thumbnailUrl,
        Instant createdAt) {

    public static AdminBannerResult from(Banner banner) {
        return new AdminBannerResult(banner.getId(), banner.getTitle(), banner.getSubtitle(),
                banner.getWebViewUrl(), banner.getThumbnailUrl(), banner.getCreatedAt());
    }
}
