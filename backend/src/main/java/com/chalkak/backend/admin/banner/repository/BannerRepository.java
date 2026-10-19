package com.chalkak.backend.admin.banner.repository;

import com.chalkak.backend.admin.banner.domain.Banner;

public interface BannerRepository {

    boolean existsByWebViewUrl(String webViewUrl);

    Banner save(Banner banner);
}
