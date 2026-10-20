package com.chalkak.backend.banner.service;

import com.chalkak.backend.banner.domain.Banner;
import java.time.Instant;
import java.util.UUID;

public record BannerResult(
        UUID id,
        String title,
        String subtitle,
        String webViewUrl,
        String thumbnailUrl,
        Instant createdAt) {

    public static BannerResult from(Banner banner) {
        return new BannerResult(banner.getId(), banner.getTitle(), banner.getSubtitle(),
                banner.getWebViewUrl(), banner.getThumbnailUrl(), banner.getCreatedAt());
    }
}
