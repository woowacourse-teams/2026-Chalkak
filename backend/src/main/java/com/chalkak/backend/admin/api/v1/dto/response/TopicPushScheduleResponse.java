package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.TopicPushScheduleResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record TopicPushScheduleResponse(
        UUID scheduleId,
        @Schema(
                description = "KST 기준 발송 시각",
                type = "string",
                format = "time",
                example = "09:00:00")
        LocalTime sendTime,
        Instant createdAt,
        Instant updatedAt
) {

    public static TopicPushScheduleResponse from(TopicPushScheduleResult result) {
        return new TopicPushScheduleResponse(
                result.scheduleId(),
                result.sendTime(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
