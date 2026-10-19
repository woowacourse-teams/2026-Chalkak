package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.banner.service.BannerResult;
import java.time.Instant;
import java.util.UUID;

public record AdminBannerResponse(
        UUID id,
        String title,
        String subtitle,
        String webViewUrl,
        String thumbnailUrl,
        Instant createdAt
) {

    public static AdminBannerResponse from(BannerResult result) {
        return new AdminBannerResponse(result.id(), result.title(), result.subtitle(),
                result.webViewUrl(), result.thumbnailUrl(), result.createdAt());
    }
}
