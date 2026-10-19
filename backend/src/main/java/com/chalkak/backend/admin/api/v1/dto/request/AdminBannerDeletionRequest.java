package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "관리자 배너 삭제 요청")
public record AdminBannerDeletionRequest(
        @NotBlank
        @Schema(
                description = "앞뒤 공백 제거 후 1~500자 삭제 사유. 길이는 Unicode code point 기준입니다.",
                example = "이벤트 종료")
        String reason
) {
}
