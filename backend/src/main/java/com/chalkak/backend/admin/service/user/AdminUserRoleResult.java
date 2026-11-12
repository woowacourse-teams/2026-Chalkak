package com.chalkak.backend.admin.service.user;

import com.chalkak.backend.user.domain.UserRole;
import java.util.UUID;

public record AdminUserRoleResult(UUID userId, UserRole role) {
}
