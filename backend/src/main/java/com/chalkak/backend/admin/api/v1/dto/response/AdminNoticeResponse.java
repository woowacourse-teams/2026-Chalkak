package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.notification.AdminNoticeResult;
import com.chalkak.backend.notification.domain.NoticeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminNoticeResponse(
        UUID noticeId,
        String title,
        String body,
        Instant scheduledAt,
        @Schema(description = "회원 알림함 배포 상태. 예약 시각이 지나 알림함에 배포되면 DISTRIBUTED")
        NoticeStatus status,
        @Schema(description = "회원 알림함에 배포한 시각. 배포 전이면 null", nullable = true)
        Instant distributedAt,
        @Schema(
                description = "공지를 삭제한 시각. 삭제하지 않았으면 null. 배포 전 삭제는 발송 취소, 배포 후 삭제는 회원 알림함에서 숨김",
                nullable = true)
        Instant deletedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminNoticeResponse from(AdminNoticeResult result) {
        return new AdminNoticeResponse(result.noticeId(), result.title(), result.body(),
                result.scheduledAt(), result.status(), result.distributedAt(), result.deletedAt(),
                result.createdAt(),
                result.updatedAt());
    }
}
