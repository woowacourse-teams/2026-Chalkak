package com.chalkak.backend.jev.service;

import com.chalkak.backend.jev.domain.JevChoiceQuestion;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JevJobClaim(
        String action,
        UUID jobId,
        UUID callId,
        Instant deadline,
        String model,
        String topic,
        String category,
        Map<String, JevChoiceQuestion> questions,
        String storageKey
) {

    public static JevJobClaim idle(String action, UUID jobId) {
        return new JevJobClaim(
                action,
                jobId,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                null
        );
    }
}
