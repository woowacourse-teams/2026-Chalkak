package com.chalkak.backend.admin.api.v1.dto.request;

import jakarta.validation.constraints.NotNull;

public record JevPromptActivationRequest(
        @NotNull
        Boolean active
) {
}
