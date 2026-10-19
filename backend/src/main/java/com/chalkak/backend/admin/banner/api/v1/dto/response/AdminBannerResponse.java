package com.chalkak.backend.admin.banner.api.v1.dto.response;

import com.chalkak.backend.admin.banner.service.AdminBannerResult;
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

    public static AdminBannerResponse from(AdminBannerResult result) {
        return new AdminBannerResponse(result.id(), result.title(), result.subtitle(),
                result.webViewUrl(), result.thumbnailUrl(), result.createdAt());
    }
}
