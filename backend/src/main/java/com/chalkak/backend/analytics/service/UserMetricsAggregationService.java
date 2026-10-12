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

@Service
@RequiredArgsConstructor
public class UserMetricsAggregationService {

    private static final Logger AGGREGATION_LOG = LoggerFactory.getLogger("chalkak.aggregation");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int WEEK_DAYS = 7;

    private final UserMetricsRepository userMetricsRepository;
    private final AccessLogMetricsReader accessLogMetricsReader;

    /**
     * KST 하루의 사용자 지표를 DB에서 세어 한 줄로 남긴다.
     *
     * <p>
     * 게시물 생성 시각으로 정해지는 {@code dau}, {@code posts}, {@code wau}는 같은 날짜를 다시 집계해도 같다.
     * {@code eligibleUsers}, {@code likesTotal}은 집계 시점의 스냅샷이라 다시 돌린 시각에 따라 달라질 수
     * 있다. 게시물 생성은 업로드 요청이 성공한 시점이므로 삭제됐거나 거절된 게시물도 센다.
     *
     * <p>
     * 로그 지표 관측 시작일 이후 날짜는 접근 로그 지표도 같은 줄에 남기고, 로그의 게시물 생성 수가 DB의 {@code posts}와 다르면
     * 경고를 남긴다. 로그 조회가 하나라도 실패하면 성공 로그 없이 예외를 던진다.
     */
    public DailyUserMetrics aggregate(LocalDate metricDate) {
        Instant dayStart = startOf(metricDate);
        Instant dayEnd = startOf(metricDate.plusDays(1));
        Instant weekStart = startOf(metricDate.minusDays(WEEK_DAYS - 1L));

        long dau = userMetricsRepository.countPostAuthors(dayStart, dayEnd);
        long posts = userMetricsRepository.countPosts(dayStart, dayEnd);
        long wau = userMetricsRepository.countPostAuthors(weekStart, dayEnd);
        long eligibleUsers = userMetricsRepository.countEligibleUsers();
        long likesTotal = userMetricsRepository.countLikesOnLivePosts();
        AccessLogMetrics accessLogMetrics = accessLogMetricsReader.read(metricDate).orElse(null);

        DailyUserMetrics metrics = new DailyUserMetrics(
                metricDate,
                dau,
                posts,
                wau,
                eligibleUsers,
                likesTotal,
                accessLogMetrics);
        warnIfPostCountsDiffer(metrics);
        log(metrics);
        return metrics;
    }

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(KST).toInstant();
    }

    private void warnIfPostCountsDiffer(DailyUserMetrics metrics) {
        AccessLogMetrics accessLogMetrics = metrics.accessLogMetrics();
        if (accessLogMetrics == null || accessLogMetrics.postCreations() == metrics.posts()) {
            return;
        }
        AGGREGATION_LOG.atWarn()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AGGREGATION)
                .addKeyValue(LogFields.EVENT, "posts_mismatch")
                .addKeyValue(LogFields.METRIC_DATE, metrics.metricDate().toString())
                .addKeyValue("dbPosts", metrics.posts())
                .addKeyValue("logPosts", accessLogMetrics.postCreations())
                .log("DB와 접근 로그의 게시물 수가 다름");
    }

    private void log(DailyUserMetrics metrics) {
        var event = AGGREGATION_LOG.atInfo()
                .addKeyValue(LogFields.TYPE, LogFields.TYPE_AGGREGATION)
                .addKeyValue(LogFields.METRIC_DATE, metrics.metricDate().toString())
                .addKeyValue("dau", metrics.dau())
                .addKeyValue("posts", metrics.posts())
                .addKeyValue("wau", metrics.wau())
                .addKeyValue("eligibleUsers", metrics.eligibleUsers())
                .addKeyValue("likesTotal", metrics.likesTotal());
        AccessLogMetrics accessLogMetrics = metrics.accessLogMetrics();
        if (accessLogMetrics != null) {
            event = event
                    .addKeyValue("visitors", accessLogMetrics.visitors())
                    .addKeyValue("topicViewers", accessLogMetrics.topicViewers())
                    .addKeyValue("requests", accessLogMetrics.requests())
                    .addKeyValue("server5xx", accessLogMetrics.server5xx())
                    .addKeyValue("latencyAvgMs", accessLogMetrics.latencyAvgMs())
                    .addKeyValue("latencyP95Ms", accessLogMetrics.latencyP95Ms());
        }
        event.log("사용자 지표 집계");
    }
}
