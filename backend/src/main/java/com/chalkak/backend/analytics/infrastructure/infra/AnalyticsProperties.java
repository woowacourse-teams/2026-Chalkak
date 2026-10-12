package com.chalkak.backend.analytics.infrastructure.infra;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code excludedUserIds}는 지표에서 뺄 회원 ID다. 팀원·테스트 계정이 사용자 지표를 흐리지 않게 한다. */
@ConfigurationProperties("chalkak.analytics")
public record AnalyticsProperties(
        List<UUID> excludedUserIds) {

    public AnalyticsProperties {
        excludedUserIds = excludedUserIds == null ? List.of() : List.copyOf(excludedUserIds);
    }
}
