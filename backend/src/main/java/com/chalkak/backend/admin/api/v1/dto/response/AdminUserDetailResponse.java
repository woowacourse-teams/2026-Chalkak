package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminUserDetail;
import com.chalkak.backend.admin.service.AdminUserStatus;
import com.chalkak.backend.auth.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID userId,
        @Schema(nullable = true)
        String email,
        AdminUserStatus status,
        @Schema(nullable = true)
        String appVersion,
        @Schema(nullable = true)
        SocialProvider socialProvider,
        SignatureResponse signature,
        AdminUserListResponse.PostCountsResponse postCounts,
        Instant createdAt,
        Instant updatedAt,
        @Schema(nullable = true)
        Instant deletedAt
) {

    public static AdminUserDetailResponse from(AdminUserDetail user) {
        return new AdminUserDetailResponse(
                user.userId(),
                user.email(),
                user.status(),
                user.appVersion(),
                user.socialProvider(),
                SignatureResponse.from(user.signature()),
                AdminUserListResponse.PostCountsResponse.from(user.postCounts()),
                user.createdAt(),
                user.updatedAt(),
                user.deletedAt());
    }

    @Schema(name = "AdminUserSignature")
    public record SignatureResponse(
            @Schema(nullable = true)
            String originalImageUrl,
            @Schema(nullable = true)
            String thumbnailImageUrl
    ) {

        private static SignatureResponse from(AdminUserDetail.Signature signature) {
            return new SignatureResponse(
                    signature.originalImageUrl(),
                    signature.thumbnailImageUrl());
        }
    }
}
