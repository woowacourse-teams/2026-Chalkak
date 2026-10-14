package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

@Schema(description = "관리자 공지 등록 요청")
public record AdminNoticeRequest(
        @NotBlank
        @Schema(description = "공지 제목. 앞뒤 공백을 제거한 뒤 1~50자", example = "서비스 점검 안내")
        String title,

        @NotBlank
        @Schema(
                description = "공지 본문. 앞뒤 공백을 제거한 뒤 1~500자",
                example = "10월 20일 02:00부터 04:00까지 서비스 점검이 진행됩니다.")
        String body,

        @NotNull
        @Schema(
                description = "발송 예약 시각. 즉시 발송은 현재 시각을 전달하며, 현재보다 5분 넘게 과거인 시각은 거절합니다.",
                example = "2026-10-19T09:00:00Z")
        Instant scheduledAt
) {
}
