package com.chalkak.backend.analytics.infrastructure.infra;

import com.chalkak.backend.analytics.service.AccessLogMetrics;
import com.chalkak.backend.analytics.service.AccessLogMetricsReader;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.QueryStatus;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.StopQueryRequest;

/**
 * CloudWatch Logs Insights로 접근 로그 지표를 센다.
 *
 * <p>
 * 관측 시작일 이전이거나 하루 전체가 로그 보존 기간 안에 들지 않는 날짜는 쿼리 없이 빈 값을 돌려준다. 보존 기간이 지난 날짜를 조회하면
 * 원본 로그가 없어 0으로 센 가짜 지표가 되기 때문이다. 동시에 돌릴 수 있는 쿼리 수가 제한돼 있어 쿼리를 하나씩 순서대로 실행한다.
 * 쿼리가 실패하거나 시간 안에 끝나지 않으면 값을 비워 두지 않고 예외를 던진다. 평균 응답 시간은 소수점 첫째 자리에서 반올림한다.
 */
@Slf4j
@Component
public class CloudWatchLogsAccessLogMetricsReader implements AccessLogMetricsReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    static final Duration POLL_INTERVAL = Duration.ofSeconds(1);
    static final Duration MAX_WAIT = Duration.ofSeconds(60);

    static final String VISITORS_QUERY = """
            filter type = "access" and ispresent(userId)\
            %s
            | stats count_distinct(userId) as visitors""";

    static final String TOPIC_VIEWERS_QUERY = """
            filter type = "access" and method = "GET" and route = "/api/v1/topics" \
            and status = 200 and ispresent(userId)\
            %s
            | stats count_distinct(userId) as topicViewers""";

    static final String REQUEST_SUMMARY_QUERY = """
            filter type = "access"
            | stats count(*) as requests, avg(durationMs) as latencyAvg, \
            pct(durationMs, 95) as latencyP95""";

    static final String SERVER_5XX_QUERY = """
            filter type = "access" and status >= 500
            | stats count(*) as server5xx""";

    static final String POST_CREATIONS_QUERY = """
            filter type = "access" and method = "POST" and route = "/api/v1/posts" and status = 201\
            %s
            | stats count(*) as postCreations""";

    private final CloudWatchLogsClient client;
    private final String logGroup;
    private final LocalDate logMetricsStartDate;
    private final Duration logRetention;
    private final Clock clock;
    private final String excludedUserFilter;
    private final Sleeper sleeper;
    private final Duration pollInterval;
    private final int maxPolls;

    @Autowired
    public CloudWatchLogsAccessLogMetricsReader(
            CloudWatchLogsClient client,
            AnalyticsProperties properties,
            Clock clock
    ) {
        this(
                client,
                properties,
                clock,
                CloudWatchLogsAccessLogMetricsReader::sleep,
                POLL_INTERVAL,
                MAX_WAIT
        );
    }

    CloudWatchLogsAccessLogMetricsReader(
            CloudWatchLogsClient client,
            AnalyticsProperties properties,
            Clock clock,
            Sleeper sleeper,
            Duration pollInterval,
            Duration maxWait
    ) {
        this.client = client;
        this.logGroup = properties.logGroup();
        this.logMetricsStartDate = properties.logMetricsStartDate();
        this.logRetention = Duration.ofDays(properties.logRetentionDays());
        this.clock = clock;
        this.excludedUserFilter = excludedUserFilter(properties.excludedUserIds());
        this.sleeper = sleeper;
        this.pollInterval = pollInterval;
        this.maxPolls = (int) (maxWait.toMillis() / pollInterval.toMillis());
    }

    @Override
    public Optional<AccessLogMetrics> read(LocalDate metricDate) {
        Instant from = metricDate.atStartOfDay(KST).toInstant();
        if (!hasLogs(metricDate, from)) {
            return Optional.empty();
        }
        Instant to = metricDate.plusDays(1).atStartOfDay(KST).toInstant();
        List<ResultField> visitors = query(
                from,
                to,
                VISITORS_QUERY.formatted(excludedUserFilter)
        );
        List<ResultField> topicViewers = query(
                from,
                to,
                TOPIC_VIEWERS_QUERY.formatted(excludedUserFilter)
        );
        List<ResultField> summary = query(
                from,
                to,
                REQUEST_SUMMARY_QUERY
        );
        List<ResultField> server5xx = query(
                from,
                to,
                SERVER_5XX_QUERY
        );
        List<ResultField> postCreations = query(
                from,
                to,
                POST_CREATIONS_QUERY.formatted(excludedUserFilter)
        );
        return Optional.of(new AccessLogMetrics(
                single(visitors, "visitors"),
                single(topicViewers, "topicViewers"),
                single(summary, "requests"),
                single(server5xx, "server5xx"),
                single(summary, "latencyAvg"),
                single(summary, "latencyP95"),
                single(postCreations, "postCreations")
        ));
    }

    /** 관측 시작일 이후이고 하루 전체가 로그 보존 기간 안에 드는 날짜인가. 하루가 일부만 만료돼도 지표가 모자라므로 없는 것으로 본다. */
    private boolean hasLogs(
            LocalDate metricDate,
            Instant dayStart
    ) {
        return !metricDate.isBefore(logMetricsStartDate)
                && !dayStart.isBefore(clock.instant().minus(logRetention));
    }

    private static String excludedUserFilter(List<UUID> excludedUserIds) {
        if (excludedUserIds.isEmpty()) {
            return "";
        }
        String ids = excludedUserIds.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        return "\n| filter userId not in [" + ids + "]";
    }

    /** 쿼리를 시작하고 끝날 때까지 기다려 첫 결과 행의 필드를 돌려준다. 결과 행이 없으면 빈 목록이다. */
    private List<ResultField> query(
            Instant from,
            Instant to,
            String queryString
    ) {
        String queryId = client.startQuery(StartQueryRequest.builder()
                .logGroupName(logGroup)
                .startTime(from.getEpochSecond())
                // Logs Insights의 endTime은 포함 경계라, 다음 날 0시 정각의 로그가 섞이지 않게 1초를 뺀다.
                .endTime(to.getEpochSecond() - 1)
                .queryString(queryString)
                .build()).queryId();
        for (int poll = 0; poll < maxPolls; poll++) {
            GetQueryResultsResponse response = client.getQueryResults(
                    GetQueryResultsRequest.builder().queryId(queryId).build());
            QueryStatus status = response.status();
            if (status == QueryStatus.COMPLETE) {
                return response.results().isEmpty() ? List.of() : response.results().getFirst();
            }
            if (status != QueryStatus.RUNNING && status != QueryStatus.SCHEDULED) {
                throw new IllegalStateException(
                        "Logs Insights 쿼리 실패: status=" + status + ", queryId=" + queryId);
            }
            sleeper.sleep(pollInterval);
        }
        stopQuietly(queryId);
        throw new IllegalStateException("Logs Insights 쿼리 시간 초과: queryId=" + queryId);
    }

    private void stopQuietly(String queryId) {
        try {
            client.stopQuery(StopQueryRequest.builder().queryId(queryId).build());
        } catch (RuntimeException e) {
            // 시간 초과 예외를 던질 것이므로 중단 실패는 남기기만 한다.
            log.warn(
                    "Logs Insights 쿼리 중단 실패: queryId={}",
                    queryId,
                    e
            );
        }
    }

    /** 필드가 없거나 숫자가 아니면 0이다. Logs Insights는 값을 문자열로 돌려주고, 일치하는 로그가 없으면 행을 비운다. */
    private static long single(
            List<ResultField> row,
            String field
    ) {
        for (ResultField resultField : row) {
            if (field.equals(resultField.field())) {
                return parse(resultField.value());
            }
        }
        return 0L;
    }

    private static long parse(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        double number = Double.parseDouble(value.trim());
        return Double.isNaN(number) ? 0L : Math.round(number);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Logs Insights 쿼리 대기 중 인터럽트", e);
        }
    }

    @FunctionalInterface
    interface Sleeper {

        void sleep(Duration duration);
    }
}
