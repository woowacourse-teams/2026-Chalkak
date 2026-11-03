package com.chalkak.backend.feedback.infrastructure.persistence;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedbackRepositoryImpl implements FeedbackRepository {

    private final FeedbackJpaRepository feedbackJpaRepository;

    @Override
    public Feedback save(Feedback feedback) {
        return feedbackJpaRepository.save(feedback);
    }

    @Override
    public List<Instant> findSubmissionTimesAfter(UUID userId, Instant createdAfter) {
        return feedbackJpaRepository.findSubmissionTimesAfter(userId, createdAfter);
    }
}
