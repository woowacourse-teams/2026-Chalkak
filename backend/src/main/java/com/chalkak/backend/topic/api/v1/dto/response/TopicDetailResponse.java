package com.chalkak.backend.topic.api.v1.dto.response;

import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.service.TopicDetail;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TopicDetailResponse(
        UUID id,
        String title,
        LocalDate topicDate,
        Instant startsAt,
        Instant endsAt,
        TopicPhase phase
) {

    public static TopicDetailResponse fromTopicDetail(TopicDetail detail) {
        return new TopicDetailResponse(
                detail.id(),
                detail.title(),
                detail.topicDate(),
                detail.startsAt(),
                detail.endsAt(),
                detail.phase()
        );
    }
}
