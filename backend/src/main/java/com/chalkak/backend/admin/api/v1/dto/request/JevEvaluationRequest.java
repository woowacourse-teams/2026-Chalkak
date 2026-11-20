package com.chalkak.backend.admin.api.v1.dto.request;

import com.chalkak.backend.jev.domain.JevDecision;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record JevEvaluationRequest(
        @NotNull
        JevDecision decision,
        @NotNull
        Map<String, @NotNull JevDecision> questions,
        @NotBlank
        @Size(max = 500)
        String reason,
        @NotNull
        JevGoldLabel.Sampling sampling
) {
}
