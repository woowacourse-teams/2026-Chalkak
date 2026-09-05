package com.chalkak.backend.notification.infrastructure.infra;

import java.net.URI;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chalkak.admin.notification")
public record NotificationProperties(
        URI adminWebBaseUrl
) {

    private static final String HTTPS_SCHEME = "https";

    public NotificationProperties {
        Objects.requireNonNull(adminWebBaseUrl, "관리자 웹 기본 주소가 필요합니다.");

        if (!isExactHttpsOrigin(adminWebBaseUrl)) {
            throw new IllegalArgumentException(
                    "관리자 웹 기본 주소는 경로 없는 HTTPS Origin이어야 합니다."
            );
        }
    }

    private static boolean isExactHttpsOrigin(URI uri) {
        if (!HTTPS_SCHEME.equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            return false;
        }
        if (uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return false;
        }
        String path = uri.getRawPath();
        return path == null || path.isEmpty();
    }
}
