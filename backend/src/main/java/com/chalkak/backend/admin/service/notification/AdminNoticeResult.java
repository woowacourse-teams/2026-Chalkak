package com.chalkak.backend.admin.service.notification;

import com.chalkak.backend.notification.domain.Notice;
import java.time.Instant;
import java.util.UUID;

public record AdminNoticeResult(
        UUID noticeId,
        String title,
        String body,
        Instant scheduledAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminNoticeResult from(Notice notice) {
        return new AdminNoticeResult(notice.getId(), notice.getTitle(), notice.getBody(),
                notice.getScheduledAt(), notice.getCreatedAt(), notice.getUpdatedAt());
    }
}
