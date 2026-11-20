package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.jev.domain.JevPromptConfiguration;
import com.chalkak.backend.jev.domain.JevPromptRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JevPromptVersionCreateRequest(
        @NotNull
        JevPromptRole role,
        @NotBlank
        String model,
        @NotNull
        JevPromptConfiguration configuration,
        @Size(max = 500)
        String memo
) {
}
