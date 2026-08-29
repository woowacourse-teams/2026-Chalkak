package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.service.SocialSignupResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record SocialSignupResponse(
        UUID userId,

        @Schema(description = "가입 직후 바로 사용할 수 있는 액세스 토큰")
        String accessToken,

        @Schema(
                description = "발급 시점부터의 액세스 토큰 유효 시간(초)",
                example = "3600"
        )
        Long expiresIn
) {

    public static SocialSignupResponse from(SocialSignupResult result) {
        return new SocialSignupResponse(
                result.userId(),
                result.accessToken().value(),
                result.accessToken().expiresIn().toSeconds());
    }
}
