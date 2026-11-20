package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.jev.domain.JevPromptConfiguration;
import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record JevPromptVersionResponse(
        UUID id,
        JevPromptRole role,
        int version,
        String model,
        JevPromptConfiguration configuration,
        String outputSchemaVersion,
        boolean active,
        @Schema(nullable = true)
        Instant activatedAt,
        @Schema(nullable = true)
        String memo,
        UUID createdByAdminId,
        Instant createdAt
) {

    public static JevPromptVersionResponse from(JevPromptVersion version) {
        return new JevPromptVersionResponse(
                version.getId(),
                version.getRole(),
                version.getVersion(),
                version.getModel(),
                version.getConfiguration(),
                version.getOutputSchemaVersion(),
                version.isActive(),
                version.getActivatedAt(),
                version.getMemo(),
                version.getCreatedByAdminId(),
                version.getCreatedAt()
        );
    }
}
