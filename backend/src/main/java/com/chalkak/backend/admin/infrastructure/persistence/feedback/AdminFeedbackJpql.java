package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdminFeedbackJpql {

    private static final String SELECT_PREFIX = """
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                feedback.deletedAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt,
            """;

    private static final String FROM_WITH_AUTHOR = """
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;
    static final String SELECT_WITH_AUTHOR = SELECT_PREFIX + "null, null" + FROM_WITH_AUTHOR;

    static final String SELECT_DETAIL_WITH_AUTHOR = SELECT_PREFIX
            + "feedback.answerContent, feedback.answeredAt" + FROM_WITH_AUTHOR;
}
