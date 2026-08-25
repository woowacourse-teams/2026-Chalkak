package com.chalkak.backend.auth.api.v1.dto.request;

import com.chalkak.backend.auth.domain.SocialProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SocialIdTokenRequest(
        @NotNull(message = "소셜 로그인 제공자는 필수입니다.")
        SocialProvider provider,
        @NotBlank(message = "ID Token은 필수입니다.")
        String idToken
) {
}
