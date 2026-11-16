package com.chalkak.backend.notification.infrastructure.infra;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;
import tools.jackson.databind.ObjectMapper;

@Component
@Profile("dev | prod")
@ConditionalOnProperty(prefix = "chalkak.notification.sqs", name = "topic-push-enabled", havingValue = "true")
class CloudWatchTopicPushResults {

    private static final String RESULT_FILTER = """
            { $.type = "notification" && $.eventId = * &&
              ($.stage = "topic_publication_summary" || $.stage = "topic_execution_skipped" ||
               $.stage = "topic_execution_failed") }
            """;

    private final CloudWatchLogsClient client;
    private final ObjectMapper objectMapper;
    private final String logGroup;

    CloudWatchTopicPushResults(
            CloudWatchLogsClient client,
            ObjectMapper objectMapper,
            @Value("${chalkak.notification.topic-monitor.log-group}") String logGroup
    ) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.logGroup = logGroup;
    }

    public Set<String> findRecordedEventIds(Instant from, Instant to) {
        FilterLogEventsRequest request = FilterLogEventsRequest.builder()
                .logGroupName(logGroup)
                .startTime(from.toEpochMilli())
                .endTime(to.toEpochMilli())
                .filterPattern(RESULT_FILTER.strip())
                .overrideConfiguration(
                        configuration -> configuration.apiCallTimeout(Duration.ofSeconds(5)))
                .build();

        Set<String> eventIds = new HashSet<>();
        for (FilteredLogEvent event : client.filterLogEventsPaginator(request).events()) {
            eventIds.add(objectMapper.readTree(event.message()).get("eventId").asString());
        }
        return eventIds;
    }
}
