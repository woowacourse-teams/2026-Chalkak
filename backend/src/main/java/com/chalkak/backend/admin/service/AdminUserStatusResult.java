package com.chalkak.backend.admin.service;

import com.chalkak.backend.user.domain.UserStatus;
import java.util.UUID;

public record AdminUserStatusResult(
        UUID userId,
        UserStatus status
) {
}
