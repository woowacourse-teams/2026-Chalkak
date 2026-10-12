package com.chalkak.backend.analytics.service;

import com.chalkak.backend.analytics.repository.UserMetricsRepository;
import com.chalkak.backend.common.logging.LogFields;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserMetricsAggregationService {

    private static final Logger AGGREGATION_LOG = LoggerFactory.getLogger("chalkak.aggregation");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int WEEK_DAYS = 7;

    private final UserMetricsRepository userMetricsRepository;

    /**
     * KST 하루의 사용자 지표를 DB에서 세어 한 줄로 남긴다.
     *
     * <p>
     * 게시물 생성 시각으로 정해지는 {@code dau}, {@code posts}, {@code wau}는 같은 날짜를 다시 집계해도 같다.
     * {@code eligibleUsers}, {@code likesTotal}은 집계 시점의 스냅샷이라 다시 돌린 시각에 따라 달라질 수
     * 있다. 게시물 생성은 업로드 요청이 성공한 시점이므로 삭제됐거나 거절된 게시물도 센다.
     */
    @Transactional(readOnly = true)
    public DailyUserMetrics aggregate(LocalDate metricDate) {
        Instant dayStart = startOf(metricDate);
        Instant dayEnd = startOf(metricDate.plusDays(1));
        Instant weekStart = startOf(metricDate.minusDays(WEEK_DAYS - 1L));

        DailyUserMetrics metrics = new DailyUserMetrics(
                metricDate,
                userMetricsRepository.countPostAuthors(dayStart, dayEnd),
                userMetricsRepository.countPosts(dayStart, dayEnd),
                userMetricsRepository.countPostAuthors(weekStart, dayEnd),
                userMetricsRepository.countEligibleUsers(),
                userMetricsRepository.countLikesOnLivePosts());
        log(metrics);
        return metrics;
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    private void log(DailyUserMetrics metrics) {
        AGGREGATION_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AGGREGATION)
                .addKeyValue(LogFields.METRIC_DATE, metrics.metricDate().toString())
                .addKeyValue("dau", metrics.dau())
                .addKeyValue("posts", metrics.posts())
                .addKeyValue("wau", metrics.wau())
                .addKeyValue("eligibleUsers", metrics.eligibleUsers())
                .addKeyValue("likesTotal", metrics.likesTotal())
                .log("사용자 지표 집계");
    }
}
