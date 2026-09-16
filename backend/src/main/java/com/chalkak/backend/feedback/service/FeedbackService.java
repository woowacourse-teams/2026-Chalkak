package com.chalkak.backend.feedback.service;

import com.chalkak.backend.feedback.domain.Feedback;
import com.chalkak.backend.feedback.repository.FeedbackRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    @Transactional
    public FeedbackSubmissionResult submit(UUID userId, String content) {
        Feedback feedback = feedbackRepository.save(Feedback.create(userId, content));
        return FeedbackSubmissionResult.from(feedback);
    }
}
