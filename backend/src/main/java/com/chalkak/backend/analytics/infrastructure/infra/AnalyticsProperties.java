package com.chalkak.backend.analytics.infrastructure.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code excludedUserIds}는 지표에서 뺄 회원 ID다. 팀원·테스트 계정이 사용자 지표를 흐리지 않게 한다.
 * {@code logGroup}은 접근 로그가 쌓이는 CloudWatch 로그 그룹이고,
 * {@code logMetricsStartDate}보다 이른 날짜는 로그 지표를 집계하지 않는다.
 */
@ConfigurationProperties("chalkak.analytics")
public record AnalyticsProperties(
        List<UUID> excludedUserIds,
        String logGroup,
        LocalDate logMetricsStartDate) {

    public AnalyticsProperties {
        excludedUserIds = excludedUserIds == null ? List.of() : List.copyOf(excludedUserIds);
    }
}
