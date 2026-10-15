package com.chalkak.backend.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.chalkak.backend.analytics.repository.UserMetricsRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** DB 조회와 로그 조회를 Mock으로 대체해 로그 지표 관측 시작일, 교차 검증, 실패 전파를 검증한다. */
class UserMetricsAggregationLogMetricsTest {

    private static final LocalDate START_DATE = LocalDate.of(2026, 10, 2);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);
    private static final long DB_POSTS = 3L;

    private final UserMetricsRepository repository = mock(UserMetricsRepository.class);
    private final AccessLogMetricsReader reader = mock(AccessLogMetricsReader.class);
    private final UserMetricsAggregationService service = new UserMetricsAggregationService(
            repository,
            reader);

    private final Logger aggregationLogger =
            (Logger) LoggerFactory.getLogger("chalkak.aggregation");
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        when(reader.read(any())).thenReturn(Optional.empty());
        when(repository.countPosts(any(), any())).thenReturn(DB_POSTS);
        appender.start();
        aggregationLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        aggregationLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    @DisplayName("접근 로그 지표가 비어 있으면 로그 지표 키를 남기지 않고 DB 지표만 남긴다")
    void aggregate_emptyLogMetrics_logsDbMetricsOnly() {
        // When
        DailyUserMetrics metrics = service.aggregate(START_DATE.minusDays(1));

        // Then
        assertThat(metrics.accessLogMetrics()).isNull();
        assertThat(appender.list).hasSize(1);
        assertThat(keys(appender.list.getFirst())).containsExactly(
                "type", "metricDate", "dau", "posts", "wau", "eligibleUsers", "likesTotal");
    }

    @Test
    @DisplayName("접근 로그 지표가 있으면 같은 줄에 지표를 남긴다")
    void aggregate_onStartDate_logsLogMetricsInSameLine() {
        // Given
        when(reader.read(any())).thenReturn(Optional.of(logMetrics(DB_POSTS)));

        // When
        DailyUserMetrics metrics = service.aggregate(START_DATE);

        // Then
        verify(reader).read(START_DATE);
        assertThat(metrics.accessLogMetrics().requests()).isEqualTo(120);
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keys(event)).containsExactly(
                "type", "metricDate", "dau", "posts", "wau", "eligibleUsers", "likesTotal",
                "visitors", "topicViewers", "requests", "server5xx",
                "latencyAvgMs", "latencyP95Ms");
        assertThat(keyValue(event, "visitors")).isEqualTo(5L);
        assertThat(keyValue(event, "topicViewers")).isEqualTo(4L);
        assertThat(keyValue(event, "requests")).isEqualTo(120L);
        assertThat(keyValue(event, "server5xx")).isEqualTo(2L);
        assertThat(keyValue(event, "latencyAvgMs")).isEqualTo(35L);
        assertThat(keyValue(event, "latencyP95Ms")).isEqualTo(210L);
    }

    @Test
    @DisplayName("로그의 게시물 생성 수가 DB posts와 다르면 WARN 로그를 남기고 집계는 성공한다")
    void aggregate_postCountMismatch_logsWarnAndSucceeds() {
        // Given
        when(reader.read(any())).thenReturn(Optional.of(logMetrics(DB_POSTS + 1)));

        // When
        DailyUserMetrics metrics = service.aggregate(START_DATE);

        // Then
        assertThat(metrics).isNotNull();
        assertThat(appender.list).hasSize(2);
        ILoggingEvent warn = appender.list.getFirst();
        assertThat(warn.getLevel()).isEqualTo(Level.WARN);
        assertThat(keyValue(warn, "type")).isEqualTo("aggregation");
        assertThat(keyValue(warn, "event")).isEqualTo("posts_mismatch");
        assertThat(keyValue(warn, "metricDate")).isEqualTo("2026-10-02");
        assertThat(keyValue(warn, "dbPosts")).isEqualTo(DB_POSTS);
        assertThat(keyValue(warn, "logPosts")).isEqualTo(DB_POSTS + 1);
        assertThat(appender.list.getLast().getLevel()).isEqualTo(Level.INFO);
    }

    @Test
    @DisplayName("게시물 수가 같으면 WARN 로그를 남기지 않는다")
    void aggregate_postCountMatches_noWarn() {
        // Given
        when(reader.read(any())).thenReturn(Optional.of(logMetrics(DB_POSTS)));

        // When
        service.aggregate(START_DATE);

        // Then
        assertThat(appender.list).noneMatch(event -> event.getLevel() == Level.WARN);
    }

    @Test
    @DisplayName("로그 조회가 실패하면 예외를 던지고 성공 로그를 남기지 않는다")
    void aggregate_readerFails_throwsWithoutSuccessLine() {
        // Given
        when(reader.read(any())).thenThrow(new IllegalStateException("Logs Insights 쿼리 실패"));

        // When
        assertThatThrownBy(() -> service.aggregate(START_DATE))
                .isInstanceOf(IllegalStateException.class);

        // Then
        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("일요일 기준일은 코호트 인원, 유지 회원 수, 유지율(소수 첫째 자리)을 같은 줄에 남긴다")
    void aggregate_sunday_logsRetentionKeys() {
        // Given
        when(repository.countPostAuthors(any(), any())).thenReturn(3L);
        when(repository.countReturningPostAuthors(any(), any(), any(), any())).thenReturn(2L);

        // When
        service.aggregate(SUNDAY);

        // Then
        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keys(event)).containsExactly(
                "type", "metricDate", "dau", "posts", "wau", "eligibleUsers", "likesTotal",
                "retentionW1Cohort", "retentionW1Retained", "retentionW1Rate",
                "retentionW2Cohort", "retentionW2Retained", "retentionW2Rate",
                "retentionW3Cohort", "retentionW3Retained", "retentionW3Rate",
                "retentionW4Cohort", "retentionW4Retained", "retentionW4Rate");
        assertThat(keyValue(event, "retentionW1Cohort")).isEqualTo(3L);
        assertThat(keyValue(event, "retentionW1Retained")).isEqualTo(2L);
        assertThat(keyValue(event, "retentionW1Rate")).isEqualTo(66.7);
    }

    @Test
    @DisplayName("코호트가 비면 유지율 키는 남기지 않고 인원과 유지 회원 수는 0으로 남긴다")
    void aggregate_emptyCohort_omitsRateKey() {
        // Given
        when(repository.countPostAuthors(any(), any())).thenReturn(0L);

        // When
        service.aggregate(SUNDAY);

        // Then
        ILoggingEvent event = appender.list.getFirst();
        assertThat(keyValue(event, "retentionW1Cohort")).isEqualTo(0L);
        assertThat(keyValue(event, "retentionW1Retained")).isEqualTo(0L);
        assertThat(keys(event)).noneMatch(key -> key.endsWith("Rate"));
    }

    private AccessLogMetrics logMetrics(long postCreations) {
        return new AccessLogMetrics(5, 4, 120, 2, 35, 210, postCreations);
    }

    private List<String> keys(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().map(pair -> pair.key).toList();
    }

    private Object keyValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
                .filter(pair -> pair.key.equals(key))
                .findFirst()
                .orElseThrow()
                .value;
    }
}
