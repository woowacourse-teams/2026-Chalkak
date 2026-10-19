package com.chalkak.backend.admin.banner.api.v1.dto.response;

import com.chalkak.backend.admin.banner.service.AdminBannerListResult;
import java.util.List;

public record AdminBannerListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminBannerResponse> banners
) {

    public static AdminBannerListResponse from(AdminBannerListResult result) {
        return new AdminBannerListResponse(result.currentPage(), result.pageSize(),
                result.hasNext(),
                result.banners().stream().map(AdminBannerResponse::from).toList());
    }
}
