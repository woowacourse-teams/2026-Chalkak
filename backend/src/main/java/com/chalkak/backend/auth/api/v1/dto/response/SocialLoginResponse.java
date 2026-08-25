package com.chalkak.backend.auth.api.v1.dto.response;

import com.chalkak.backend.auth.service.SocialLoginResult;
import com.chalkak.backend.auth.service.SocialLoginStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SocialLoginResponse(
        SocialLoginStatus status,
        UUID userId
) {

    public static SocialLoginResponse from(SocialLoginResult result) {
        return new SocialLoginResponse(result.status(), result.userId());
    }
}
