package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.CurrentAdminResult;
import java.util.UUID;

public record CurrentAdminResponse(
        UUID adminId,
        String username
) {

    public static CurrentAdminResponse from(CurrentAdminResult result) {
        return new CurrentAdminResponse(result.adminId(), result.username());
    }
}
