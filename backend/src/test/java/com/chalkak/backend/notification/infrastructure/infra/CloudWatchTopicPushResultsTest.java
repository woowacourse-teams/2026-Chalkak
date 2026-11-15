package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.paginators.FilterLogEventsIterable;
import tools.jackson.databind.ObjectMapper;

class CloudWatchTopicPushResultsTest {

    @Test
    @DisplayName("빈 첫 페이지 뒤의 정상·생략·실패 회차 ID까지 모두 읽는다")
    void findRecordedEventIds_withEmptyFirstPage_readsAllPages() {
        // Given
        CloudWatchLogsClient client = mock(CloudWatchLogsClient.class);
        given(client.filterLogEventsPaginator(any(FilterLogEventsRequest.class)))
                .willAnswer(call -> new FilterLogEventsIterable(client, call.getArgument(0)));
        given(client.filterLogEvents(any(FilterLogEventsRequest.class)))
                .willReturn(FilterLogEventsResponse.builder().nextToken("next").build(),
                        FilterLogEventsResponse.builder()
                                .events(
                                        event("normal", "topic_publication_summary"),
                                        event("skipped", "topic_execution_skipped"),
                                        event("failed", "topic_execution_failed")
                                ).build());
        Instant from = Instant.parse("2026-11-14T10:00:00Z");
        Instant to = from.plusSeconds(300);
        CloudWatchTopicPushResults results = new CloudWatchTopicPushResults(
                client,
                new ObjectMapper(),
                "/chalkak/dev/notification"
        );

        // When
        var eventIds = results.findRecordedEventIds(from, to);

        // Then
        assertThat(eventIds).containsExactlyInAnyOrder(
                "normal",
                "skipped",
                "failed"
        );
        ArgumentCaptor<FilterLogEventsRequest> capture = ArgumentCaptor
                .forClass(FilterLogEventsRequest.class);
        verify(client).filterLogEventsPaginator(capture.capture());
        FilterLogEventsRequest request = capture.getValue();
        assertThat(request.logGroupName()).isEqualTo("/chalkak/dev/notification");
        assertThat(request.startTime()).isEqualTo(from.toEpochMilli());
        assertThat(request.endTime()).isEqualTo(to.toEpochMilli());
        assertThat(request.filterPattern()).contains(
                "topic_publication_summary",
                "topic_execution_skipped",
                "topic_execution_failed"
        );
    }

    private FilteredLogEvent event(String eventId, String stage) {
        return FilteredLogEvent.builder()
                .message("""
                        {"eventId":"%s","stage":"%s"}
                        """.formatted(eventId, stage))
                .build();
    }
}
