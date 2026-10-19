package com.chalkak.backend.admin.api.v1.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record AdminBannerRequest(
        @NotBlank
        @Schema(description = "앞뒤 공백 제거 후 1~50자")
        String title,

        @NotBlank
        @Schema(description = "앞뒤 공백 제거 후 1~100자")
        String subtitle,

        @NotBlank
        @Schema(
                description = "앞뒤 공백 제거 후 최대 2,000자의 HTTPS 절대 주소. 명시한 포트는 65535 이하여야 하며 전체 문자열로 중복을 판단합니다.",
                example = "https://chalkak.vercel.app/event")
        String webViewUrl,

        @NotBlank
        @Schema(
                description = "앞뒤 공백 제거 후 최대 2,000자의 HTTPS 절대 주소. 명시한 포트는 65535 이하여야 합니다.",
                example = "https://cdn.example.com/banner.png")
        String thumbnailUrl
) {
}
