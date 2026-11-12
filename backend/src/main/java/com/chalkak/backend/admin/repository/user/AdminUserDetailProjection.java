package com.chalkak.backend.admin.repository.user;

import com.chalkak.backend.auth.domain.identity.SocialProvider;
import com.chalkak.backend.user.domain.UserRole;
import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminUserDetailProjection(
        UUID userId,
        String email,
        UserStatus userStatus,
        UserRole role,
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
