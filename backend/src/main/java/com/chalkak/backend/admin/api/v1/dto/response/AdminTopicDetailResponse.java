package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminTopicDetail;
import com.chalkak.backend.topic.domain.TopicPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(description = "관리자 주제 상세")
public record AdminTopicDetailResponse(
        @Schema(format = "uuid") UUID topicId,
        String title,
        LocalDate topicDate,
        Instant startsAt,
        Instant endsAt,
        TopicPhase phase,
        AdminTopicPostCounts postCounts,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminTopicDetailResponse from(AdminTopicDetail detail) {
        return new AdminTopicDetailResponse(
                detail.topicId(),
                detail.title(),
                detail.topicDate(),
                detail.startsAt(),
                detail.endsAt(),
                detail.phase(),
                AdminTopicPostCounts.from(detail.postCounts()),
                detail.createdAt(),
                detail.updatedAt()
        );
    }
}
