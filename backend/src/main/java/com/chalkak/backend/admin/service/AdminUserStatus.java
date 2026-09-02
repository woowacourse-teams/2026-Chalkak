package com.chalkak.backend.admin.service;

import com.chalkak.backend.user.domain.UserStatus;
import java.time.Instant;

public enum AdminUserStatus {
    ACTIVE,
    BANNED,
    WITHDRAWN;

    public static AdminUserStatus from(UserStatus status, Instant deletedAt) {
        if (deletedAt != null) {
            return WITHDRAWN;
        }
        if (status == UserStatus.BANNED) {
            return BANNED;
        }
        return ACTIVE;
    }
}
