package com.chalkak.backend.admin.api.v1.dto.response;

import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.domain.JevPhase;
import com.chalkak.backend.jev.domain.JevTopicCategory;
import java.time.Instant;
import java.util.UUID;

public record JevRunResponse(
        UUID jobId,
        UUID postId,
        UUID topicId,
        String topic,
        boolean preview,
        UUID classificationId,
        String category,
        JevPhase classificationCalls,
        JevEvaluation classificationEvaluation,
        JevPhase photoCalls,
        JevEvaluation evaluation,
        int dispatchAttempts,
        String dispatchError,
        Instant createdAt,
        Instant finishedAt
) {

    public static JevRunResponse from(JevModerationRun run, JevTopicCategory category) {
        return new JevRunResponse(
                run.getId(),
                run.getPostId(),
                run.getTopicId(),
                run.getTopicTitle(),
                run.isPreview(),
                category.getId(),
                category.getCategory(),
                category.getPhase(),
                category.getEvaluation(),
                run.getPhase(),
                run.getEvaluation(),
                run.getDispatchAttempts(),
                run.getDispatchError(),
                run.getCreatedAt(),
                run.getFinishedAt()
        );
    }
}
