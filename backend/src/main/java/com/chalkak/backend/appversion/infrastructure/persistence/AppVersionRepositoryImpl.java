package com.chalkak.backend.appversion.infrastructure.persistence;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AppVersionRepositoryImpl implements AppVersionRepository {

    private final AppVersionJpaRepository jpaRepository;

    @Override
    public Optional<AppVersion> findByPlatform(AppPlatform platform) {
        return jpaRepository.findByPlatform(platform);
    }
}
