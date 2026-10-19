package com.chalkak.backend.admin.banner.repository;

import com.chalkak.backend.admin.banner.domain.Banner;
import com.chalkak.backend.admin.repository.AdminPage;

public interface BannerRepository {

    boolean existsByWebViewUrl(String webViewUrl);

    Banner save(Banner banner);

    AdminPage<Banner> findBanners(int page, int pageSize);
}
