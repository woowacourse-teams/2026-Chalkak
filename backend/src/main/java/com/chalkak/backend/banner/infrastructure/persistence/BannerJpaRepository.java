package com.chalkak.backend.banner.infrastructure.persistence;

import com.chalkak.backend.banner.domain.Banner;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerJpaRepository extends JpaRepository<Banner, UUID> {

    Slice<Banner> findAllBy(Pageable pageable);

    boolean existsByWebViewUrl(String webViewUrl);
}
