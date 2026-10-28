package com.chalkak.backend.appversion.service;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.repository.AppVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppVersionService {

    private final AppVersionRepository appVersionRepository;

    @Transactional(readOnly = true)
    public AppVersionResult getAppVersion(AppPlatform platform) {
        return appVersionRepository.findByPlatform(platform)
                .map(AppVersionResult::from)
                .orElseGet(() -> AppVersionResult.createUnconfigured(platform));
    }
}
