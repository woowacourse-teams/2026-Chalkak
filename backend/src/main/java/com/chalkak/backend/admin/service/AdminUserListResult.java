package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminUserQueryPage;
import com.chalkak.backend.admin.repository.AdminUserSummaryProjection;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<UserSummary> users
) {

    public static AdminUserListResult from(AdminUserQueryPage page) {
        return new AdminUserListResult(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.users().stream()
                        .map(UserSummary::from)
                        .toList());
    }

    public record UserSummary(
            UUID userId,
            String email,
            AdminUserStatus status,
            String appVersion,
            SocialProvider socialProvider,
            PostCounts postCounts,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {

        private static UserSummary from(AdminUserSummaryProjection user) {
            return new UserSummary(
                    user.userId(),
                    user.email(),
                    AdminUserStatus.from(user.userStatus(), user.deletedAt()),
                    user.appVersion(),
                    user.socialProvider(),
                    PostCounts.from(
                            user.pendingPostCount(),
                            user.approvedPostCount(),
                            user.rejectedPostCount()),
                    user.createdAt(),
                    user.updatedAt(),
                    user.deletedAt());
        }
    }

    public record PostCounts(
            long pending,
            long approved,
            long rejected
    ) {

        private static PostCounts from(
                long pending,
                long approved,
                long rejected
        ) {
            return new PostCounts(
                    pending,
                    approved,
                    rejected);
        }
    }
}
