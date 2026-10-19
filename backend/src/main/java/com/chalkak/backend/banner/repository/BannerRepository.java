package com.chalkak.backend.banner.repository;

import com.chalkak.backend.banner.domain.Banner;
import java.util.Optional;
import java.util.UUID;

public interface BannerRepository {

    boolean existsByWebViewUrl(String webViewUrl);

    Banner save(Banner banner);

    Optional<Banner> findByIdForUpdate(UUID bannerId);

    BannerPage findBanners(int page, int pageSize);
}
