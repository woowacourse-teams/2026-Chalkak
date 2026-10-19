package com.chalkak.backend.admin.banner.service;

import com.chalkak.backend.admin.banner.domain.Banner;
import com.chalkak.backend.admin.repository.AdminPage;
import java.util.List;

public record AdminBannerListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminBannerResult> banners) {

    public static AdminBannerListResult from(AdminPage<Banner> page) {
        return new AdminBannerListResult(page.currentPage(), page.pageSize(), page.hasNext(),
                page.items().stream().map(AdminBannerResult::from).toList());
    }
}
