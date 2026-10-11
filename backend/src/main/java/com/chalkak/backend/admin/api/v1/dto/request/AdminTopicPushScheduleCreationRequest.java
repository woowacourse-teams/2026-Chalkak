package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record AdminTopicPushScheduleCreationRequest(
        @NotNull
        @Schema(
                description = "KST 기준 발송 시각. 날짜·시간대 없이 HH:mm:ss 형식으로 전달하며 초는 00이어야 합니다.",
                type = "string",
                format = "time",
                example = "09:00:00")
        LocalTime sendTime) {
}
