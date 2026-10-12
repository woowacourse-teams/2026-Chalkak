package com.chalkak.backend.analytics.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.chalkak.backend.analytics.service.AccessLogMetrics;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.QueryStatus;
import software.amazon.awssdk.services.cloudwatchlogs.model.ResultField;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.StartQueryResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.StopQueryRequest;

class CloudWatchLogsAccessLogMetricsReaderTest {

    private static final String LOG_GROUP = "/chalkak/test/application";
    private static final LocalDate METRIC_DATE = LocalDate.of(2026, 10, 2);

    private final CloudWatchLogsClient client = mock(CloudWatchLogsClient.class);
    private final List<Duration> sleeps = new ArrayList<>();

    @Test
    @DisplayName("로그 그룹과 KST 하루의 epoch 초 구간으로 쿼리를 시작한다")
    void read_startsQueriesWithLogGroupAndEpochSecondWindow() {
        // Given
        stubQueries(completed("0"));

        // When
        reader(List.of()).read(METRIC_DATE);

        // Then
        List<StartQueryRequest> requests = startRequests(5);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.logGroupName()).isEqualTo(LOG_GROUP);
            assertThat(request.startTime()).isEqualTo(1_790_866_800L);
            assertThat(request.endTime()).isEqualTo(1_790_953_199L);
        });
    }

    @Test
    @DisplayName("쿼리 문자열에 기대한 필터와 집계가 들어간다")
    void read_queryStrings_containExpectedFilters() {
        // Given
        stubQueries(completed("0"));

        // When
        reader(List.of()).read(METRIC_DATE);

        // Then
        assertThat(startRequests(5)).extracting(StartQueryRequest::queryString).containsExactly(
                "filter type = \"access\" and ispresent(userId)\n"
                        + "| stats count_distinct(userId) as visitors",
                "filter type = \"access\" and method = \"GET\" and route = \"/api/v1/topics\" "
                        + "and status = 200 and ispresent(userId)\n"
                        + "| stats count_distinct(userId) as topicViewers",
                "filter type = \"access\"\n"
                        + "| stats count(*) as requests, avg(durationMs) as latencyAvg, "
                        + "pct(durationMs, 95) as latencyP95",
                "filter type = \"access\" and status >= 500\n"
                        + "| stats count(*) as server5xx",
                "filter type = \"access\" and method = \"POST\" and route = \"/api/v1/posts\" "
                        + "and status = 201\n"
                        + "| stats count(*) as postCreations");
    }

    @Test
    @DisplayName("제외 회원이 있으면 회원 기준 쿼리에만 제외 조건을 붙이고 시스템 상태 쿼리에는 붙이지 않는다")
    void read_excludedUsers_filtersOnlyUserBasedQueries() {
        // Given
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        stubQueries(completed("0"));

        // When
        reader(List.of(first, second)).read(METRIC_DATE);

        // Then
        String clause = "| filter userId not in [\"" + first + "\",\"" + second + "\"]";
        List<String> queries =
                startRequests(5).stream().map(StartQueryRequest::queryString).toList();
        assertThat(queries.get(0)).contains(clause);
        assertThat(queries.get(1)).contains(clause);
        assertThat(queries.get(2)).doesNotContain("not in");
        assertThat(queries.get(3)).doesNotContain("not in");
        assertThat(queries.get(4)).contains(clause);
    }

    @Test
    @DisplayName("제외 회원이 없으면 제외 조건을 붙이지 않는다")
    void read_noExcludedUsers_hasNoExcludeClause() {
        // Given
        stubQueries(completed("0"));

        // When
        reader(List.of()).read(METRIC_DATE);

        // Then
        assertThat(startRequests(5)).noneMatch(request -> request.queryString().contains("not in"));
    }

    @Test
    @DisplayName("결과 필드를 지표로 매핑하고 평균 응답 시간은 반올림한다")
    void read_parsesResultFields() {
        // Given
        when(client.startQuery(any(StartQueryRequest.class)))
                .thenReturn(
                        started("q1"), started("q2"), started("q3"), started("q4"), started("q5"));
        stubResult("q1", completed(row("visitors", "7")));
        stubResult("q2", completed(row("topicViewers", "6")));
        stubResult("q3", completed(
                row("requests", "1200", "latencyAvg", "12.6", "latencyP95", "80.4")));
        stubResult("q4", completed(row("server5xx", "3")));
        stubResult("q5", completed(row("postCreations", "9")));

        // When
        AccessLogMetrics metrics = reader(List.of()).read(METRIC_DATE).orElseThrow();

        // Then
        assertThat(metrics).isEqualTo(new AccessLogMetrics(7, 6, 1200, 3, 13, 80, 9));
    }

    @Test
    @DisplayName("결과 행이 없으면 모든 지표를 0으로 센다")
    void read_emptyResults_returnsZeros() {
        // Given
        stubQueries(GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(List.of())
                .build());

        // When
        AccessLogMetrics metrics = reader(List.of()).read(METRIC_DATE).orElseThrow();

        // Then
        assertThat(metrics).isEqualTo(new AccessLogMetrics(0, 0, 0, 0, 0, 0, 0));
    }

    @Test
    @DisplayName("값이 비었거나 NaN이면 0으로 센다")
    void read_blankOrNanValues_areZero() {
        // Given
        stubQueries(completed(row("visitors", "", "topicViewers", "NaN", "requests", "5")));

        // When
        AccessLogMetrics metrics = reader(List.of()).read(METRIC_DATE).orElseThrow();

        // Then
        assertThat(metrics.visitors()).isZero();
        assertThat(metrics.topicViewers()).isZero();
    }

    @Test
    @DisplayName("Running 상태면 간격을 두고 다시 조회해 Complete를 받는다")
    void read_runningThenComplete_pollsWithInterval() {
        // Given
        when(client.startQuery(any(StartQueryRequest.class))).thenReturn(started("q"));
        GetQueryResultsResponse running =
                GetQueryResultsResponse.builder().status(QueryStatus.RUNNING).build();
        when(client.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(running, running, completed("1"));

        // When
        reader(List.of()).read(METRIC_DATE);

        // Then
        // 첫 쿼리가 Running 두 번 뒤 Complete, 나머지 네 쿼리는 바로 Complete
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    @DisplayName("Failed, Cancelled, Timeout, Unknown 상태면 예외를 던진다")
    void read_terminalFailureStatus_throws() {
        // Given
        List<QueryStatus> statuses = List.of(
                QueryStatus.FAILED,
                QueryStatus.CANCELLED,
                QueryStatus.TIMEOUT,
                QueryStatus.UNKNOWN_TO_SDK_VERSION);

        // When & Then
        for (QueryStatus status : statuses) {
            when(client.startQuery(any(StartQueryRequest.class))).thenReturn(started("q"));
            when(client.getQueryResults(any(GetQueryResultsRequest.class)))
                    .thenReturn(GetQueryResultsResponse.builder().status(status).build());

            assertThatThrownBy(() -> reader(List.of()).read(METRIC_DATE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("q");
        }
    }

    @Test
    @DisplayName("대기 한도를 넘기면 쿼리를 중단하고 예외를 던진다")
    void read_exceedsMaxWait_stopsQueryAndThrows() {
        // Given
        when(client.startQuery(any(StartQueryRequest.class))).thenReturn(started("slow"));
        when(client.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(GetQueryResultsResponse.builder().status(QueryStatus.RUNNING).build());

        // When
        assertThatThrownBy(() -> reader(List.of()).read(METRIC_DATE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("시간 초과");

        // Then
        // 60초 한도, 1초 간격이면 60번 조회하고 60번 기다린다.
        verify(client, times(60)).getQueryResults(any(GetQueryResultsRequest.class));
        assertThat(sleeps).hasSize(60);
        ArgumentCaptor<StopQueryRequest> stop = ArgumentCaptor.forClass(StopQueryRequest.class);
        verify(client).stopQuery(stop.capture());
        assertThat(stop.getValue().queryId()).isEqualTo("slow");
    }

    @Test
    @DisplayName("한 쿼리가 실패하면 이후 쿼리를 시작하지 않는다")
    void read_firstQueryFails_stopsRemainingQueries() {
        // Given
        when(client.startQuery(any(StartQueryRequest.class))).thenReturn(started("q"));
        when(client.getQueryResults(any(GetQueryResultsRequest.class)))
                .thenReturn(GetQueryResultsResponse.builder().status(QueryStatus.FAILED).build());

        // When
        assertThatThrownBy(() -> reader(List.of()).read(METRIC_DATE))
                .isInstanceOf(IllegalStateException.class);

        // Then
        verify(client, times(1)).startQuery(any(StartQueryRequest.class));
        verify(client, never()).stopQuery(any(StopQueryRequest.class));
    }

    @Test
    @DisplayName("관측 시작일 이전 날짜는 쿼리를 시작하지 않고 빈 값을 돌려준다")
    void read_beforeStartDate_returnsEmptyWithoutQueries() {
        // When & Then
        assertThat(reader(List.of()).read(METRIC_DATE.minusDays(1))).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("관측 시작일 당일부터는 쿼리를 실행한다")
    void read_onStartDate_runsQueries() {
        // Given
        stubQueries(completed("1"));

        // When & Then
        assertThat(reader(List.of()).read(METRIC_DATE)).isPresent();
    }

    private CloudWatchLogsAccessLogMetricsReader reader(List<UUID> excludedUserIds) {
        return new CloudWatchLogsAccessLogMetricsReader(
                client,
                new AnalyticsProperties(excludedUserIds, LOG_GROUP, LocalDate.of(2026, 10, 2)),
                sleeps::add,
                Duration.ofSeconds(1),
                Duration.ofSeconds(60));
    }

    private void stubQueries(GetQueryResultsResponse response) {
        when(client.startQuery(any(StartQueryRequest.class))).thenReturn(started("q"));
        when(client.getQueryResults(any(GetQueryResultsRequest.class))).thenReturn(response);
    }

    private void stubResult(
            String queryId,
            GetQueryResultsResponse response
    ) {
        when(client.getQueryResults(GetQueryResultsRequest.builder().queryId(queryId).build()))
                .thenReturn(response);
    }

    private List<StartQueryRequest> startRequests(int expectedCount) {
        ArgumentCaptor<StartQueryRequest> captor = ArgumentCaptor.forClass(StartQueryRequest.class);
        verify(client, times(expectedCount)).startQuery(captor.capture());
        return captor.getAllValues();
    }

    private StartQueryResponse started(String queryId) {
        return StartQueryResponse.builder().queryId(queryId).build();
    }

    private GetQueryResultsResponse completed(String value) {
        return completed(row(
                "visitors", value,
                "topicViewers", value,
                "requests", value,
                "server5xx", value,
                "latencyAvg", value,
                "latencyP95", value,
                "postCreations", value));
    }

    private GetQueryResultsResponse completed(List<ResultField> row) {
        return GetQueryResultsResponse.builder()
                .status(QueryStatus.COMPLETE)
                .results(List.of(row))
                .build();
    }

    private List<ResultField> row(String... fieldAndValues) {
        List<ResultField> fields = new ArrayList<>();
        for (int i = 0; i < fieldAndValues.length; i += 2) {
            fields.add(ResultField.builder()
                    .field(fieldAndValues[i])
                    .value(fieldAndValues[i + 1])
                    .build());
        }
        return fields;
    }
}
