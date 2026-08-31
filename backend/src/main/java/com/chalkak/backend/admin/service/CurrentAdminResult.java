package com.chalkak.backend.admin.service;

import java.util.UUID;

public record CurrentAdminResult(
        UUID adminId,
        String username
) {
}
