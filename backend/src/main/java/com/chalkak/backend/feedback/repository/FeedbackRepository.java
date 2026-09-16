package com.chalkak.backend.feedback.repository;

import com.chalkak.backend.feedback.domain.Feedback;

public interface FeedbackRepository {

    Feedback save(Feedback feedback);
}
