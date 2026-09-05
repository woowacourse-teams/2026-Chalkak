package com.chalkak.backend.admin.repository;

import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminUserDetailProjection(
        UUID userId,
        String email,
        UserStatus userStatus,
        String appVersion,
        SocialProvider socialProvider,
        String signatureOriginalStorageKey,
        String signatureThumbnailStorageKey,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        long pendingPostCount,
        long approvedPostCount,
        long rejectedPostCount
) {
}
