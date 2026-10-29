package com.chalkak.backend.appversion.service;

import com.chalkak.backend.appversion.domain.AppPlatform;
import com.chalkak.backend.appversion.domain.AppVersion;

public record AppVersionResult(
        AppPlatform platform,
        String minimumSupportedVersion,
        String storeUrl
) {

    public static AppVersionResult from(AppVersion appVersion) {
        return new AppVersionResult(
                appVersion.getPlatform(),
                appVersion.getMinimumSupportedVersion().value(),
                appVersion.getStoreUrl().value()
        );
    }

    public static AppVersionResult createUnconfigured(AppPlatform platform) {
        return new AppVersionResult(
                platform,
                null,
                null
        );
    }
}
