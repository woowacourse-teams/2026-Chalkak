package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.user.AdminUserRoleResult;
import com.chalkak.backend.user.domain.UserRole;
import java.util.UUID;

public record AdminUserRoleResponse(UUID userId, UserRole role) {

    public static AdminUserRoleResponse from(AdminUserRoleResult result) {
        return new AdminUserRoleResponse(result.userId(), result.role());
    }
}
