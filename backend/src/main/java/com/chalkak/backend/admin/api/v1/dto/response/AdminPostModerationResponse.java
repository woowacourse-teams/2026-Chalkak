package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.admin.service.AdminPostModerationResult;
import com.chalkak.backend.post.domain.ModerationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record AdminPostModerationResponse(
        UUID postId,
        @Schema(allowableValues = {"APPROVED", "REJECTED"})
        ModerationStatus moderationStatus,
        UUID moderatedBy,
        Instant moderatedAt,
        @Schema(nullable = true)
        String rejectionReason
) {

    public static AdminPostModerationResponse from(AdminPostModerationResult result) {
        return new AdminPostModerationResponse(
                result.postId(),
                result.moderationStatus(),
                result.moderatedBy(),
                result.moderatedAt(),
                result.rejectionReason()
        );
    }
}
