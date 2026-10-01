package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.user.AdminUserPostCounts;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AdminUserPostCounts")
public record AdminUserPostCountsResponse(
        long pending,
        long approved,
        long rejected) {

    public static AdminUserPostCountsResponse from(AdminUserPostCounts postCounts) {
        return new AdminUserPostCountsResponse(
                postCounts.pending(),
                postCounts.approved(),
                postCounts.rejected());
    }
}
