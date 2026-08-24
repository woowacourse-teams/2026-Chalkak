package com.chalkak.backend.topic.service;

import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TopicDetail(
        UUID id,
        String title,
        LocalDate topicDate,
        TopicPhase phase
) {

    public static TopicDetail from(Topic topic, Instant now) {
        return new TopicDetail(
                topic.getId(),
                topic.getTitle(),
                topic.getTopicDate(),
                topic.getParticipationPeriod().phaseAt(now)
        );
    }
}
