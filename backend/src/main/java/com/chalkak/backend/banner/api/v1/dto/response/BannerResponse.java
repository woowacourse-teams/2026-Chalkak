package com.chalkak.backend.banner.api.v1.dto.response;

import com.chalkak.backend.banner.service.BannerResult;
import java.time.Instant;
import java.util.UUID;

public record BannerResponse(
        UUID id,
        String title,
        String subtitle,
        String webViewUrl,
        String thumbnailUrl,
        Instant createdAt
) {

    public static BannerResponse from(BannerResult result) {
        return new BannerResponse(
                result.id(),
                result.title(),
                result.subtitle(),
                result.webViewUrl(),
                result.thumbnailUrl(),
                result.createdAt()
        );
    }
}
