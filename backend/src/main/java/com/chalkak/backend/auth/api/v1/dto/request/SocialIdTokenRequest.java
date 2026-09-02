package com.chalkak.backend.auth.api.v1.dto.request;

import com.chalkak.backend.auth.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialIdTokenRequest(
        @Schema(
                description = "ID Token 발급 소셜 로그인 제공자. GOOGLE, KAKAO 지원",
                allowableValues = {"GOOGLE", "KAKAO"}
        )
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        SocialProvider provider,
        @NotBlank(message = "ID Token은 필수입니다.")
        String idToken
) {
}
