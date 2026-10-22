package com.chalkak.backend.banner.infrastructure.persistence;

import com.chalkak.backend.banner.domain.Banner;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BannerJpaRepository extends JpaRepository<Banner, UUID> {

    Slice<Banner> findByDeletedAtIsNull(Pageable pageable);

    boolean existsByWebViewUrlAndDeletedAtIsNull(String webViewUrl);

    // 재삭제 요청도 최초 삭제 결과를 읽도록 삭제 여부와 관계없이 같은 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT banner FROM Banner banner WHERE banner.id = :bannerId")
    Optional<Banner> findByIdForUpdate(@Param("bannerId") UUID bannerId);
}
