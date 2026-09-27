package com.chalkak.backend.admin.service.user;

import com.chalkak.backend.admin.repository.AdminPage;
import com.chalkak.backend.admin.repository.user.AdminUserStatus;
import com.chalkak.backend.admin.repository.user.AdminUserSummaryProjection;
import com.chalkak.backend.auth.domain.SocialProvider;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserListResult(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<UserSummary> users) {

    public static AdminUserListResult from(AdminPage<AdminUserSummaryProjection> page) {
        return new AdminUserListResult(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.items().stream()
                        .map(UserSummary::from)
                        .toList());
    }

    public record UserSummary(
            UUID userId,
            String email,
            AdminUserStatus status,
            String appVersion,
            SocialProvider socialProvider,
            AdminUserPostCounts postCounts,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {

        private static UserSummary from(AdminUserSummaryProjection user) {
            return new UserSummary(
                    user.userId(),
                    user.email(),
                    AdminUserStatus.from(user.userStatus(), user.deletedAt()),
                    user.appVersion(),
                    user.socialProvider(),
                    new AdminUserPostCounts(
                            user.pendingPostCount(),
                            user.approvedPostCount(),
                            user.rejectedPostCount()),
                    user.createdAt(),
                    user.updatedAt(),
                    user.deletedAt());
        }
    }

}
