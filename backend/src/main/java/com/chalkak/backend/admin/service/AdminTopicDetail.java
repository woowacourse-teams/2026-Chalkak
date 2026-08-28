package com.chalkak.backend.admin.service;

import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AdminTopicDetail(
        UUID topicId,
        String title,
        LocalDate topicDate,
        Instant startsAt,
        Instant endsAt,
        TopicPhase phase,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminTopicDetail from(Topic topic, Instant now) {
        return new AdminTopicDetail(
                topic.getId(),
                topic.getTitle(),
                topic.getTopicDate(),
                topic.getParticipationPeriod().getStartsAt(),
                topic.getParticipationPeriod().getEndsAt(),
                topic.phaseAt(now),
                topic.getCreatedAt(),
                topic.getUpdatedAt()
        );
    }
}
