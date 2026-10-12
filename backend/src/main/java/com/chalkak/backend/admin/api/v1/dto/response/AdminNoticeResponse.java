package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.AdminNoticeResult;
import java.time.Instant;
import java.util.UUID;

public record AdminNoticeResponse(
        UUID noticeId,
        String title,
        String body,
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminNoticeResponse from(AdminNoticeResult result) {
        return new AdminNoticeResponse(result.noticeId(), result.title(), result.body(),
                result.scheduledAt(), result.createdAt(), result.updatedAt());
    }
}
