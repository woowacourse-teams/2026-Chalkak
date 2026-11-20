package com.chalkak.backend.jev.api.internal.v1.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record JevJobResultRequest(
        @NotBlank
        String callId,
        @NotBlank
        @Pattern(regexp = "CLASSIFICATION|PHOTO")
        String stage,
        @Size(max = 65536)
        String rawResponse,
        @Pattern(
                regexp = "CONNECT_ERROR|TIMEOUT|NETWORK_ERROR|HTTP_ERROR|INVALID_RESPONSE|RESPONSE_TOO_LARGE|IMAGE_PREPROCESSING_ERROR|IMAGE_READ_ERROR|CONFIGURATION_ERROR")
        String errorCode,
        @Min(100)
        @Max(599)
        Integer httpStatus,
        @Min(0)
        Long latencyMs,
        @Min(0)
        @Max(86400)
        Integer retryAfterSeconds
) {
}
