package com.chalkak.backend.notification.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.topic.repository.TopicRepository;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
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

    @ParameterizedTest
    @ValueSource(strings = {"dev", "prod"})
    @DisplayName("주제 푸시가 활성화된 배포 환경에서 해당 환경의 알림 로그 그룹을 조회한다")
    void findRecordedEventIds_deploymentProfiles_queryOwnLogGroup(String profile) {
        // Given
        CloudWatchLogsClient client = mock(CloudWatchLogsClient.class);
        given(client.filterLogEventsPaginator(any(FilterLogEventsRequest.class)))
                .willAnswer(call -> new FilterLogEventsIterable(client, call.getArgument(0)));
        given(client.filterLogEvents(any(FilterLogEventsRequest.class)))
                .willReturn(FilterLogEventsResponse.builder().build());

        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(CloudWatchTopicPushResults.class,
                        TopicPushOccurrenceMonitor.class)
                .withBean(CloudWatchLogsClient.class, () -> client)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(TopicPushScheduleRepository.class,
                        () -> mock(TopicPushScheduleRepository.class))
                .withBean(TopicRepository.class, () -> mock(TopicRepository.class))
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues(
                        "spring.config.import=",
                        "spring.profiles.active=" + profile,
                        "chalkak.notification.sqs.topic-push-enabled=true"
                )
                .run(context -> {
                    // When
                    assertThat(context).hasSingleBean(TopicPushOccurrenceMonitor.class);
                    context.getBean(CloudWatchTopicPushResults.class)
                            .findRecordedEventIds(Instant.EPOCH, Instant.EPOCH.plusSeconds(1));

                    // Then
                    ArgumentCaptor<FilterLogEventsRequest> capture = ArgumentCaptor
                            .forClass(FilterLogEventsRequest.class);
                    verify(client).filterLogEventsPaginator(capture.capture());
                    assertThat(capture.getValue().logGroupName())
                            .isEqualTo("/chalkak/" + profile + "/notification");
                });
    }

    private FilteredLogEvent event(String eventId, String stage) {
        return FilteredLogEvent.builder()
                .message("""
                        {"eventId":"%s","stage":"%s"}
                        """.formatted(eventId, stage))
                .build();
    }
}
