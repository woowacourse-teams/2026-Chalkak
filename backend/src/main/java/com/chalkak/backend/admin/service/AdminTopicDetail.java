package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminTopicProjection;
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
        PostCounts postCounts,
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
                new PostCounts(0, 0, 0),
                topic.getCreatedAt(),
                topic.getUpdatedAt()
        );
    }

    public static AdminTopicDetail from(AdminTopicProjection topic, Instant now) {
        TopicPhase phase = new com.chalkak.backend.topic.domain.ParticipationPeriod(
                topic.startsAt(),
                topic.endsAt()
        ).phaseAt(now);
        return new AdminTopicDetail(
                topic.topicId(),
                topic.title(),
                topic.topicDate(),
                topic.startsAt(),
                topic.endsAt(),
                phase,
                new PostCounts(
                        topic.pendingPostCount(),
                        topic.approvedPostCount(),
                        topic.rejectedPostCount()
                ),
                topic.createdAt(),
                topic.updatedAt()
        );
    }

    public record PostCounts(
            long pending,
            long approved,
            long rejected
    ) {
    }
}
