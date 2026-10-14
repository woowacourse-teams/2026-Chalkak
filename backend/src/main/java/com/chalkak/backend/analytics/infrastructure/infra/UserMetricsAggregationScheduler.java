package com.chalkak.backend.analytics.infrastructure.infra;

import com.chalkak.backend.analytics.service.UserMetricsAggregationRunner;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 매일 새벽 전날(KST)의 사용자 지표를 집계해 로그로 남긴다.
 *
 * <p>
 * 지표 알람은 성공 로그가 없는 것을 감지하므로 실패를 삼키지 않는다. 오류 로그는 러너가 남기고 예외는 그대로 던진다. 집계를 켜는 환경은
 * {@code chalkak.analytics.aggregation.enabled}로 정하며(기본 꺼짐)
 * {@link UserMetricsAggregationConfiguration}이 전용 스레드와 함께 등록한다.
 */
@RequiredArgsConstructor
public class UserMetricsAggregationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserMetricsAggregationRunner aggregationRunner;
    private final Clock clock;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul", scheduler = "analyticsAggregationScheduler")
    public void aggregateYesterday() {
        LocalDate metricDate = LocalDate.now(clock.withZone(KST)).minusDays(1);
        aggregationRunner.run(metricDate);
    }
}
