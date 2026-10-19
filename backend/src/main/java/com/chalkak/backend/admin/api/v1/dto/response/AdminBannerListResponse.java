package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.banner.service.BannerListResult;
import java.util.List;

public record AdminBannerListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminBannerResponse> banners
) {

    public static AdminBannerListResponse from(BannerListResult result) {
        return new AdminBannerListResponse(result.currentPage(), result.pageSize(),
                result.hasNext(),
                result.banners().stream().map(AdminBannerResponse::from).toList());
    }
}
