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
                author.deletedAt,
                feedback.answeredAt,
                feedback.answerDeletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;

    static final String INQUIRY_FILTERS = """
            (:#{#criteria.inquiryStatus().name()} = 'ALL'
                OR (:#{#criteria.inquiryStatus().name()} = 'NEEDS_ANSWER'
                    AND feedback.deletedAt IS NULL
                    AND author.deletedAt IS NULL
                    AND (feedback.answeredAt IS NULL OR feedback.answerDeletedAt IS NOT NULL))
                OR (:#{#criteria.inquiryStatus().name()} = 'ANSWERED'
                    AND feedback.deletedAt IS NULL
                    AND feedback.answeredAt IS NOT NULL
                    AND feedback.answerDeletedAt IS NULL)
                OR (:#{#criteria.inquiryStatus().name()} = 'INQUIRY_DELETED' AND feedback.deletedAt IS NOT NULL))
            AND (:#{#criteria.bannedAuthorOnly()} = false
                OR (author.deletedAt IS NULL AND author.status = :#{T(com.chalkak.backend.user.domain.UserStatus).BANNED}))
            """;

    static final String SELECT_DETAIL_WITH_AUTHOR = """
            SELECT new com.chalkak.backend.admin.repository.feedback.AdminFeedbackDetailProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                feedback.deletedAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt,
                CASE WHEN feedback.answerDeletedAt IS NULL THEN feedback.answerContent ELSE null END,
                CASE WHEN feedback.answerDeletedAt IS NULL THEN feedback.answeredAt ELSE null END,
                feedback.answerDeletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;
}
