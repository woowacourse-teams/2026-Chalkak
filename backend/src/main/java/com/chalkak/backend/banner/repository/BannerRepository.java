package com.chalkak.backend.banner.repository;

import com.chalkak.backend.banner.domain.Banner;

public interface BannerRepository {

    boolean existsByWebViewUrl(String webViewUrl);

    Banner save(Banner banner);

    BannerPage findBanners(int page, int pageSize);
}
