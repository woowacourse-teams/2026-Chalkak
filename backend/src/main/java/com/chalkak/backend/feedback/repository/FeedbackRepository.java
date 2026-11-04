package com.chalkak.backend.feedback.repository;

import com.chalkak.backend.feedback.domain.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeedbackRepository {

    Feedback save(Feedback feedback);

    Optional<Feedback> findVisibleByIdAndUserId(UUID feedbackId, UUID userId);

    Optional<Feedback> findByIdForUpdate(UUID feedbackId);

    Optional<Feedback> findByIdAndUserIdForUpdate(UUID feedbackId, UUID userId);

    FeedbackSlice findVisibleByUserId(
            UUID userId,
            int page,
            int pageSize
    );

    List<Instant> findSubmissionTimesAfter(UUID userId, Instant createdAfter);
}
