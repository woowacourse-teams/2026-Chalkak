package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminUserListResult;
import com.chalkak.backend.admin.service.AdminUserStatus;
import com.chalkak.backend.auth.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<UserResponse> users
) {

    public static AdminUserListResponse from(AdminUserListResult result) {
        return new AdminUserListResponse(
                result.currentPage(),
                result.pageSize(),
                result.hasNext(),
                result.users().stream()
                        .map(UserResponse::from)
                        .toList());
    }

    @Schema(name = "AdminUserListItem")
    public record UserResponse(
            UUID userId,
            @Schema(nullable = true)
            String email,
            AdminUserStatus status,
            @Schema(nullable = true)
            String appVersion,
            @Schema(nullable = true)
            SocialProvider socialProvider,
            PostCountsResponse postCounts,
            Instant createdAt,
            Instant updatedAt,
            @Schema(nullable = true)
            Instant deletedAt
    ) {

        private static UserResponse from(AdminUserListResult.UserSummary user) {
            return new UserResponse(
                    user.userId(),
                    user.email(),
                    user.status(),
                    user.appVersion(),
                    user.socialProvider(),
                    PostCountsResponse.from(user.postCounts()),
                    user.createdAt(),
                    user.updatedAt(),
                    user.deletedAt());
        }
    }

    @Schema(name = "AdminUserPostCounts")
    public record PostCountsResponse(
            long pending,
            long approved,
            long rejected
    ) {

        public static PostCountsResponse from(AdminUserListResult.PostCounts postCounts) {
            return new PostCountsResponse(
                    postCounts.pending(),
                    postCounts.approved(),
                    postCounts.rejected());
        }
    }
}
