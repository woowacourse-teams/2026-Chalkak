package com.chalkak.backend.user.service;

public record ArtistEligibilityResult(
        long receivedLikeCount,
        long requiredLikeCount,
        boolean canApply
) {
}
