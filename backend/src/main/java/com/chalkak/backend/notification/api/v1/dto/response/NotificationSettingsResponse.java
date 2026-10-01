package com.chalkak.backend.notification.api.v1.dto.response;

import com.chalkak.backend.notification.service.NotificationSettingsResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingsResponse(
        @Schema(description = "주제 푸시 수신 여부")
        boolean topicPushEnabled,
        @Schema(description = "게시물 승인·반려 결과 푸시 수신 여부")
        boolean moderationPushEnabled) {

    public static NotificationSettingsResponse from(NotificationSettingsResult result) {
        return new NotificationSettingsResponse(
                result.topicPushEnabled(), result.moderationPushEnabled());
    }
}
