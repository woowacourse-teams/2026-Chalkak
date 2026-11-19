package com.chalkak.backend.jev.api.internal.v1.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JevJobClaimRequest(
        @NotBlank
        String jobId) {
}
