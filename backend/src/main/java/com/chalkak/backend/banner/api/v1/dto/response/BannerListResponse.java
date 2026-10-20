package com.chalkak.backend.banner.api.v1.dto.response;

import com.chalkak.backend.banner.service.BannerListResult;
import java.util.List;

public record BannerListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<BannerResponse> banners
) {

    public static BannerListResponse from(BannerListResult result) {
        return new BannerListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.banners().stream().map(BannerResponse::from).toList()
        );
    }
}
