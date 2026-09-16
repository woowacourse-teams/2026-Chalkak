package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.repository.AdminFeedbackQueryPage;
import com.chalkak.backend.admin.repository.AdminFeedbackQueryRepository;
import com.chalkak.backend.admin.repository.AdminFeedbackQuerySort;
import com.chalkak.backend.admin.repository.AdminFeedbackSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminFeedbackQueryRepositoryImpl implements AdminFeedbackQueryRepository {

    private static final String FEEDBACK_SELECT = """
            SELECT new com.chalkak.backend.admin.repository.AdminFeedbackSummaryProjection(
                feedback.id,
                feedback.content,
                feedback.createdAt,
                author.id,
                author.email,
                author.status,
                author.appVersion,
                author.deletedAt
            )
            FROM Feedback feedback
            JOIN User author ON author.id = feedback.userId
            """;

    private final EntityManager entityManager;

    @Override
    public AdminFeedbackQueryPage findFeedbacks(
            AdminFeedbackQuerySort sort,
            int page,
            int pageSize
    ) {
        TypedQuery<AdminFeedbackSummaryProjection> query = entityManager.createQuery(
                FEEDBACK_SELECT + order(sort),
                AdminFeedbackSummaryProjection.class
        );
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);

        List<AdminFeedbackSummaryProjection> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<AdminFeedbackSummaryProjection> feedbacks = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);
        return new AdminFeedbackQueryPage(feedbacks, page, pageSize, hasNext);
    }

    private String order(AdminFeedbackQuerySort sort) {
        if (sort == AdminFeedbackQuerySort.CREATED_AT_ASC) {
            return " ORDER BY feedback.createdAt ASC, feedback.id ASC";
        }
        return " ORDER BY feedback.createdAt DESC, feedback.id DESC";
    }
}
