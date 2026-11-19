package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.jev.domain.JevPromptDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JevPreviewCreateRequest(
        @NotBlank
        String postId,
        @NotBlank
        String topicId,
        @NotNull
        JevPromptDraft draft
) {
}
