package com.chalkak.backend.jev.api.internal.v1.dto.response;

import com.chalkak.backend.jev.domain.JevChoiceQuestion;
import com.chalkak.backend.jev.service.JevJobClaim;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record JevJobClaimResponse(
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

    public static JevJobClaimResponse from(JevJobClaim claim) {
        return new JevJobClaimResponse(
                claim.action(),
                claim.jobId(),
                claim.callId(),
                claim.deadline(),
                claim.model(),
                claim.topic(),
                claim.category(),
                claim.questions(),
                claim.storageKey()
        );
    }
}
