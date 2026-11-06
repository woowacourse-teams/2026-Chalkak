package com.chalkak.backend.admin.service.feedback;

import java.time.Instant;

public enum AdminFeedbackAnswerState {
    UNANSWERED, ANSWERED, DELETED;

    static AdminFeedbackAnswerState from(Instant answeredAt, Instant answerDeletedAt) {
        if (answerDeletedAt != null) {
            return DELETED;
        }
        return answeredAt == null ? UNANSWERED : ANSWERED;
    }
}
