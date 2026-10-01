package com.chalkak.backend.admin.service.auth;

import java.util.UUID;

public record CurrentAdminResult(
        UUID adminId,
        String username) {
}
