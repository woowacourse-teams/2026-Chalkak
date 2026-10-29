package com.chalkak.backend.appversion.infrastructure.persistence;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionJpaRepository extends JpaRepository<AppVersion, UUID> {

    Optional<AppVersion> findByPlatform(AppPlatform platform);
}
