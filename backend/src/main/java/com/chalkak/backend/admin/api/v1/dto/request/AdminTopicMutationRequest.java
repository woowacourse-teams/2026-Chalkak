package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

@Schema(description = "관리자 주제 등록·수정 요청")
public record AdminTopicMutationRequest(
        @NotBlank
        @Size(max = 255)
        @Schema(description = "주제 제목", example = "오늘 가장 기억에 남은 순간")
        String title,

        @NotNull
        @Schema(description = "한국 기준 주제 날짜", example = "2026-08-30")
        LocalDate topicDate,

        @NotNull
        @Schema(description = "참여 시작 시각", example = "2026-08-29T15:00:00Z")
        Instant startsAt,

        @NotNull
        @Schema(description = "참여 종료 시각", example = "2026-08-30T15:00:00Z")
        Instant endsAt
) {
}
