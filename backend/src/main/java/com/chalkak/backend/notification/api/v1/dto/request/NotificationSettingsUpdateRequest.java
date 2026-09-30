package com.chalkak.backend.notification.api.v1.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

public record NotificationSettingsUpdateRequest(
        @Schema(description = "주제 푸시 수신 여부. 생략 또는 null이면 기존 값을 유지합니다.", nullable = true)
        Boolean topicPushEnabled,
        @Schema(description = "승인·반려 푸시 수신 여부. 생략 또는 null이면 기존 값을 유지합니다.", nullable = true)
        Boolean moderationPushEnabled
) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "변경할 푸시 수신 설정을 하나 이상 입력해주세요.")
    public boolean isUpdatePresent() {
        return topicPushEnabled != null || moderationPushEnabled != null;
    }
}
