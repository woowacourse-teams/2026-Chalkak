package com.chalkak.backend.admin.service.banner;

import com.chalkak.backend.banner.repository.BannerPage;
import java.util.List;

public record AdminBannerListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<AdminBannerResult> banners) {

    public static AdminBannerListResult from(BannerPage page) {
        return new AdminBannerListResult(page.currentPage(), page.pageSize(), page.hasNext(),
                page.items().stream().map(AdminBannerResult::from).toList());
    }
}
