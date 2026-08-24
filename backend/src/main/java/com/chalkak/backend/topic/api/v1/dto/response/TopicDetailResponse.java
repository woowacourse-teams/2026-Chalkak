package com.chalkak.backend.topic.api.v1.dto.response;

import com.chalkak.backend.topic.domain.TopicPhase;
import com.chalkak.backend.topic.service.TopicDetail;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

public record TopicDetailResponse(
        UUID id,
        String title,
        LocalDate topicDate,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        TopicPhase phase
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static TopicDetailResponse fromTopicDetail(TopicDetail detail) {
        return new TopicDetailResponse(
                detail.id(),
                detail.title(),
                detail.topicDate(),
                detail.startsAt().atZone(KST).toOffsetDateTime(),
                detail.endsAt().atZone(KST).toOffsetDateTime(),
                detail.phase()
        );
    }
}
