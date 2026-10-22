package com.chalkak.backend.notification.api.v1.dto.response;

import com.chalkak.backend.notification.domain.NotificationType;
import com.chalkak.backend.notification.domain.NotificationSourceType;
import com.chalkak.backend.notification.service.NotificationListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record NotificationListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<NotificationSummaryResponse> notifications
) {

    public static NotificationListResponse from(NotificationListResult result) {
        return new NotificationListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.notifications().stream()
                        .map(NotificationSummaryResponse::from)
                        .toList()
        );
    }

    public record NotificationSummaryResponse(
            UUID id,
            @Schema(
                    description = "알림 종류. 승인·반려(POST_APPROVED·POST_REJECTED), AI 리뷰 결과(AI_REVIEW_COMPLETED·AI_REVIEW_FAILED), 주제(TOPIC), 공지(NOTICE)")
            NotificationType type,
            @Schema(
                    description = "관련 대상 종류. 승인·반려·AI 리뷰 결과 알림은 POST이며 대상이 없으면 null",
                    nullable = true)
            NotificationSourceType sourceType,
            @Schema(
                    description = "관련 대상 ID. 승인 알림은 이 게시물 ID로 이동하고 AI 리뷰 결과 알림은 이 게시물의 AI 리뷰 화면으로 이동하며 알림 ID와 구분",
                    nullable = true)
            UUID sourceId,
            String title,
            String body,
            @Schema(description = "알림함에 표시할 사진 썸네일 URL", nullable = true)
            String thumbnailImageUrl,
            Instant readAt,
            Instant createdAt
    ) {

        private static NotificationSummaryResponse from(NotificationListResult.Summary summary) {
            return new NotificationSummaryResponse(
                    summary.id(),
                    summary.type(),
                    summary.sourceType(),
                    summary.sourceId(),
                    summary.title(),
                    summary.body(),
                    summary.thumbnailImageUrl(),
                    summary.readAt(),
                    summary.createdAt()
            );
        }
    }
}
