package com.chalkak.backend.admin.infrastructure.persistence.feedback;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AdminFeedbackJpql {

    static final String SELECT_WITH_AUTHOR = """
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackProjection(
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
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;
}
