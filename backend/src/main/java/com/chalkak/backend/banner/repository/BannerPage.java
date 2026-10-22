package com.chalkak.backend.banner.repository;

import com.chalkak.backend.banner.domain.Banner;
import java.util.List;

public record BannerPage(
        List<Banner> items,
        int currentPage,
        int pageSize,
        boolean hasNext
) {

    public BannerPage {
        items = List.copyOf(items);
    }
}
