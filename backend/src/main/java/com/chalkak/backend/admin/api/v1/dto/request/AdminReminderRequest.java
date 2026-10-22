package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(description = "관리자 리마인더 등록 요청")
public record AdminReminderRequest(
        @NotBlank
        @Schema(description = "푸시 제목. 앞뒤 공백을 제거한 뒤 1~50자", example = "오늘이 얼마 남지 않았어요")
        String title,

        @NotBlank
        @Schema(description = "푸시 본문. 앞뒤 공백을 제거한 뒤 1~500자", example = "사진을 자랑해 주세요!")
        String body,

        @NotNull
        @Schema(description = "반복 방식. ONCE는 발송 시각에 한 번, DAILY는 발송 시각의 날짜부터 매일 같은 KST 시각에 보냅니다.")
        ReminderRepeat repeat,

        @NotNull
        @Schema(
                description = "발송 대상. 전체(ALL), 오늘 주제 참여자(TOPIC_PARTICIPANTS), 오늘 주제 미참여자(TOPIC_NON_PARTICIPANTS)")
        ReminderTarget target,

        @NotNull
        @Schema(
                description = "발송 시각. 매일 반복이면 첫 발송 날짜와 매일 보낼 시각입니다. 현재보다 과거인 시각은 거절합니다.",
                example = "2026-10-25T12:00:00Z")
        Instant scheduledAt
) {
}
