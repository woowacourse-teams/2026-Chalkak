package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.AdminReminderResult;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminReminderResponse(
        UUID reminderId,
        String title,
        String body,
        ReminderRepeat repeat,
        ReminderTarget target,
        @Schema(description = "발송 시각. 매일 반복이면 첫 발송 날짜와 매일 보낼 시각")
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminReminderResponse from(AdminReminderResult result) {
        return new AdminReminderResponse(
                result.reminderId(),
                result.title(),
                result.body(),
                result.repeat(),
                result.target(),
                result.scheduledAt(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
