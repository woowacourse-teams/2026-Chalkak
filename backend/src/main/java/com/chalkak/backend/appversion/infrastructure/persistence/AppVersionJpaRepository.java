package com.chalkak.backend.appversion.infrastructure.persistence;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppVersionJpaRepository extends JpaRepository<AppVersion, UUID> {

    Optional<AppVersion> findByPlatform(AppPlatform platform);

    // 행이 없는 최초 저장도 플랫폼 단위로 직렬화한다. 848은 앱 버전 설정의 잠금 네임스페이스다.
    @Query(value = "SELECT pg_advisory_xact_lock(848, hashtext(:platform))", nativeQuery = true)
    void lockPlatform(@Param("platform") String platform);
}
