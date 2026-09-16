package com.chalkak.backend.feedback.infrastructure.persistence;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
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
}
