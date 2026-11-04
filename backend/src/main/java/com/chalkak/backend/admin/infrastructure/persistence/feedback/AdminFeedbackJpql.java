package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdminFeedbackJpql {

    private static final String FEEDBACK_PROJECTION = """
            new com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                feedback.deletedAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt
            )
            """;

    private static final String FROM_WITH_AUTHOR = """
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;
    static final String SELECT_WITH_AUTHOR = "SELECT " + FEEDBACK_PROJECTION + FROM_WITH_AUTHOR;

    static final String SELECT_DETAIL_WITH_AUTHOR = """
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection(
            """ + FEEDBACK_PROJECTION + """
                , feedback.answerContent, feedback.answeredAt
            )
            """ + FROM_WITH_AUTHOR;
}
