package com.chalkak.backend.admin.banner.infrastructure.persistence;

import com.chalkak.backend.admin.banner.domain.Banner;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerJpaRepository extends JpaRepository<Banner, UUID> {

    Slice<Banner> findAllBy(Pageable pageable);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM banners
                WHERE sha256(web_view_url::bytea) = sha256(CAST(:webViewUrl AS bytea))
                    AND web_view_url = :webViewUrl
            )
            """, nativeQuery = true)
    boolean existsByWebViewUrl(@Param("webViewUrl") String webViewUrl);
}
