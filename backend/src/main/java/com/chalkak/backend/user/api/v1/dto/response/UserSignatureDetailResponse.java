package com.chalkak.backend.user.api.v1.dto.response;

import com.chalkak.backend.user.service.UserSignatureResult;

public record UserSignatureDetailResponse(
        String signatureOriginalImageUrl,
        String signatureThumbnailImageUrl
) {

    public static UserSignatureDetailResponse from(UserSignatureResult result) {
        return new UserSignatureDetailResponse(
                result.originalImageUrl(),
                result.thumbnailImageUrl());
    }
}
