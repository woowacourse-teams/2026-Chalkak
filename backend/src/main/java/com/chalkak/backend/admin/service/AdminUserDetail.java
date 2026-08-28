package com.chalkak.backend.admin.service;

import com.chalkak.backend.admin.repository.AdminUserDetailProjection;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import java.time.Instant;
import java.util.UUID;

public record AdminUserDetail(
        UUID userId,
        String email,
        AdminUserStatus status,
        String appVersion,
        SocialProvider socialProvider,
        Signature signature,
        AdminUserListResult.PostCounts postCounts,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {

    public static AdminUserDetail from(
            AdminUserDetailProjection user,
            ImageUrlProvider imageUrlProvider
    ) {
        AdminUserStatus status = AdminUserStatus.from(
                user.userStatus(),
                user.deletedAt());
        return new AdminUserDetail(
                user.userId(),
                user.email(),
                status,
                user.appVersion(),
                user.socialProvider(),
                Signature.from(user, status, imageUrlProvider),
                postCounts(user),
                user.createdAt(),
                user.updatedAt(),
                user.deletedAt());
    }

    private static AdminUserListResult.PostCounts postCounts(
            AdminUserDetailProjection user
    ) {
        long total = user.validatingPostCount()
                + user.pendingPostCount()
                + user.approvedPostCount()
                + user.rejectedPostCount();
        return new AdminUserListResult.PostCounts(
                total,
                user.validatingPostCount(),
                user.pendingPostCount(),
                user.approvedPostCount(),
                user.rejectedPostCount());
    }

    public record Signature(
            String originalImageUrl,
            String thumbnailImageUrl
    ) {

        private static Signature from(
                AdminUserDetailProjection user,
                AdminUserStatus status,
                ImageUrlProvider imageUrlProvider
        ) {
            if (status == AdminUserStatus.WITHDRAWN) {
                return new Signature(null, null);
            }
            return new Signature(
                    toImageUrl(user.signatureOriginalStorageKey(), imageUrlProvider),
                    toImageUrl(user.signatureThumbnailStorageKey(), imageUrlProvider));
        }

        private static String toImageUrl(
                String storageKey,
                ImageUrlProvider imageUrlProvider
        ) {
            if (storageKey == null) {
                return null;
            }
            return imageUrlProvider.getUrl(storageKey);
        }
    }
}
