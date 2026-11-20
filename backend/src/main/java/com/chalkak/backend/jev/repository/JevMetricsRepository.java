package com.chalkak.backend.jev.repository;

import com.chalkak.backend.jev.domain.JevDisposition;
import com.chalkak.backend.jev.domain.JevEvaluation;
import com.chalkak.backend.jev.domain.JevGoldLabel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JevMetricsRepository {

    List<Sample> findSamples(
            Instant from,
            Instant to,
            Boolean preview,
            int limit
    );

    List<Call> findCalls(List<UUID> runIds);

    record Sample(
            UUID id,
            UUID postId,
            UUID topicId,
            boolean preview,
            JevDisposition disposition,
            JevEvaluation evaluation,
            JevGoldLabel gold,
            String repeatKey
    ) {
    }

    record Call(
            UUID id,
            String error,
            Integer httpStatus,
            Long latencyMs,
            Long inputTokens,
            Long outputTokens
    ) {
    }
}
