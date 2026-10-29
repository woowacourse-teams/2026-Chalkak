package com.chalkak.backend.appversion.repository;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import java.util.Optional;

public interface AppVersionRepository {

    Optional<AppVersion> findByPlatform(AppPlatform platform);

    void lockPlatform(AppPlatform platform);

    AppVersion saveAndFlush(AppVersion setting);
}
