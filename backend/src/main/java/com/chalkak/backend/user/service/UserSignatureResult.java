package com.chalkak.backend.user.service;

public record UserSignatureResult(
        String originalImageUrl,
        String thumbnailImageUrl
) {
}
