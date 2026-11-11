package com.chalkak.backend.user.api.v1.dto.response;

import com.chalkak.backend.user.service.ArtistEligibilityResult;

public record ArtistEligibilityResponse(
        long receivedLikeCount,
        long requiredLikeCount,
        boolean canApply
) {

    public static ArtistEligibilityResponse from(ArtistEligibilityResult result) {
        return new ArtistEligibilityResponse(
                result.receivedLikeCount(),
                result.requiredLikeCount(),
                result.canApply()
        );
    }
}
