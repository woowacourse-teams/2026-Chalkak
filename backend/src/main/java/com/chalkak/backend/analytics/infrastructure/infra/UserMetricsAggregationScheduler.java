package com.chalkak.backend.analytics.infrastructure.infra;

import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 전날(KST)의 사용자 지표를 집계해 로그로 남긴다.
 *
 * <p>
 * 지표 알람은 성공 로그가 없는 것을 감지하므로 실패를 삼키지 않는다. 오류 로그는 러너가 남기고 예외는 그대로 던진다. 집계를 켜는 환경은
 * {@code chalkak.analytics.aggregation.enabled}로 정하며 기본은 꺼져 있다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.analytics.aggregation", name = "enabled", havingValue = "true")
public class UserMetricsAggregationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserMetricsAggregationRunner aggregationRunner;
    private final Clock clock;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void aggregateYesterday() {
        LocalDate metricDate = LocalDate.now(clock.withZone(KST)).minusDays(1);
        aggregationRunner.run(metricDate);
    }
}
