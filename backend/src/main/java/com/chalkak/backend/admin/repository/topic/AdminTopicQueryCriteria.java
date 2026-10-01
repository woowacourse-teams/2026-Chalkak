package com.chalkak.backend.admin.repository.topic;

import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Instant;
import java.time.LocalDate;

public record AdminTopicQueryCriteria(
        TopicPhase phase,
        LocalDate dateFrom,
        LocalDate dateTo,
        AdminTopicSort sort,
        Instant now) {
}
