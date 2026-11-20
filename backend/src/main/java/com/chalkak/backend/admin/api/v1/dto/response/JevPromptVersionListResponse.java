package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.jev.domain.JevPromptRole;
import com.chalkak.backend.jev.domain.JevPromptVersion;
import com.chalkak.backend.jev.repository.JevPromptVersionRepository.VersionPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JevPromptVersionListResponse(
        int currentPage,
        int pageSize,
        boolean hasNext,
        List<Summary> promptVersions
) {

    public static JevPromptVersionListResponse from(VersionPage page) {
        return new JevPromptVersionListResponse(
                page.currentPage(),
                page.pageSize(),
                page.hasNext(),
                page.items().stream().map(Summary::from).toList()
        );
    }

    public record Summary(
            UUID id,
            JevPromptRole role,
            int version,
            String model,
            boolean active,
            @Schema(nullable = true)
            String memo,
            Instant createdAt
    ) {

        public static Summary from(JevPromptVersion prompt) {
            return new Summary(
                    prompt.getId(),
                    prompt.getRole(),
                    prompt.getVersion(),
                    prompt.getModel(),
                    prompt.isActive(),
                    prompt.getMemo(),
                    prompt.getCreatedAt()
            );
        }
    }
}
