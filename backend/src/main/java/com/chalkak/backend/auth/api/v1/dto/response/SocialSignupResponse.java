package com.chalkak.backend.auth.api.v1.dto.response;

import java.util.UUID;

public record SocialSignupResponse(UUID userId) {

    public static SocialSignupResponse from(UUID userId) {
        return new SocialSignupResponse(userId);
    }
}
