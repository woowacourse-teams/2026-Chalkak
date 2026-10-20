package com.chalkak.backend.banner.service;

import com.chalkak.backend.banner.repository.BannerPage;
import java.util.List;

public record BannerListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<BannerResult> banners) {

    public static BannerListResult from(BannerPage page) {
        return new BannerListResult(page.currentPage(), page.pageSize(), page.hasNext(),
                page.items().stream().map(BannerResult::from).toList());
    }
}
