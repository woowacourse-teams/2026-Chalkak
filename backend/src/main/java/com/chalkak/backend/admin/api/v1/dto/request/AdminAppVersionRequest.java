package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminAppVersionRequest(
        @Schema(
                description = "관리자 조회에서 받은 revision. 미설정 플랫폼의 최초 저장은 null입니다.",
                types = {
                        "integer",
                        "null"
                })
        @PositiveOrZero
        Long revision,
        @Schema(description = "각 숫자 0~2147483647, 앞자리 0 금지의 major.minor.patch", example = "1.10.0")
        @NotBlank
        String minimumSupportedVersion,
        @Schema(
                description = "플랫폼별 공식 HTTPS 앱 상세 주소. 실제 자사 앱과 출시 여부는 관리자가 확인합니다.",
                maxLength = 2000)
        @NotBlank
        String storeUrl,
        @Schema(description = "앞뒤 공백 제거 후 1~500 Unicode code point", maxLength = 500)
        @NotBlank
        String reason
) {
}
