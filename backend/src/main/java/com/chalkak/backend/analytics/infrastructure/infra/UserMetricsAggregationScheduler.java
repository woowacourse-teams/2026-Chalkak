package com.chalkak.backend.analytics.infrastructure.infra;

import com.chalkak.backend.analytics.service.UserMetricsAggregationService;
import com.chalkak.backend.common.logging.LogFields;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 매일 새벽 전날(KST)의 사용자 지표를 집계해 로그로 남긴다.
 *
 * <p>
 * 지표 알람은 성공 로그가 없는 것을 감지하므로 실패를 삼키지 않는다. 오류를 남기고 그대로 던진다. 집계를 켜는 환경은
 * {@code chalkak.analytics.aggregation.enabled}로 정하며 기본은 꺼져 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chalkak.analytics.aggregation", name = "enabled", havingValue = "true")
public class UserMetricsAggregationScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final UserMetricsAggregationService aggregationService;
    private final Clock clock;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Seoul")
    public void aggregateYesterday() {
        LocalDate metricDate = LocalDate.now(clock.withZone(KST)).minusDays(1);
        try {
            aggregationService.aggregate(metricDate);
        } catch (RuntimeException e) {
            log.atError()
                    .addKeyValue(LogFields.TYPE, LogFields.TYPE_ERROR)
                    .addKeyValue(LogFields.METRIC_DATE, metricDate.toString())
                    .setCause(e)
                    .log("사용자 지표 집계 실패");
            throw e;
        }
    }
}
