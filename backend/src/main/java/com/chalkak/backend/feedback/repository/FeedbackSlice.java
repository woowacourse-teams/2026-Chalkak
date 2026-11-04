package com.chalkak.backend.feedback.repository;

import com.chalkak.backend.feedback.domain.Feedback;
import java.util.List;

public record FeedbackSlice(
        List<Feedback> feedbacks,
        boolean hasNext
) {
}
