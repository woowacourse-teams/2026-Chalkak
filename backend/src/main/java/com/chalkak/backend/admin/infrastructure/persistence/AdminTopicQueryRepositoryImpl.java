package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.repository.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.AdminTopicQueryPage;
import com.chalkak.backend.admin.repository.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.AdminTopicQuerySort;
import com.chalkak.backend.admin.repository.AdminTopicProjection;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.topic.domain.TopicPhase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminTopicQueryRepositoryImpl implements AdminTopicQueryRepository {

    private static final String TOPIC_SELECT = """
            SELECT new com.chalkak.backend.admin.repository.AdminTopicProjection(
                topic.id,
                topic.title,
                topic.topicDate,
                topic.participationPeriod.startsAt,
                topic.participationPeriod.endsAt,
                topic.createdAt,
                topic.updatedAt,
                SUM(CASE WHEN post.moderationStatus = :pendingStatus THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :approvedStatus THEN 1 ELSE 0 END),
                SUM(CASE WHEN post.moderationStatus = :rejectedStatus THEN 1 ELSE 0 END)
            )
            FROM Topic topic
            LEFT JOIN Post post ON post.topic = topic AND post.deletedAt IS NULL
            """;
    private static final String TOPIC_GROUP_BY = """
             GROUP BY topic.id, topic.title, topic.topicDate,
                      topic.participationPeriod.startsAt,
                      topic.participationPeriod.endsAt,
                      topic.createdAt, topic.updatedAt
            """;

    private final EntityManager entityManager;

    @Override
    public AdminTopicQueryPage findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        StringBuilder queryText = new StringBuilder(TOPIC_SELECT)
                .append(" WHERE topic.deletedAt IS NULL");
        List<QueryParameter> parameters = new ArrayList<>();
        appendPhase(queryText, parameters, criteria);
        appendFilter(
                queryText,
                parameters,
                criteria.dateFrom(),
                " AND topic.topicDate >= :dateFrom",
                "dateFrom"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.dateTo(),
                " AND topic.topicDate <= :dateTo",
                "dateTo"
        );
        queryText.append(TOPIC_GROUP_BY);
        appendOrder(queryText, criteria.sort());

        TypedQuery<AdminTopicProjection> query = entityManager.createQuery(
                queryText.toString(),
                AdminTopicProjection.class
        );
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        setModerationStatuses(query);
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);
        List<AdminTopicProjection> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<AdminTopicProjection> topics = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);
        return new AdminTopicQueryPage(topics, page, pageSize, hasNext);
    }

    @Override
    public Optional<AdminTopicProjection> findActiveTopicById(UUID topicId) {
        TypedQuery<AdminTopicProjection> query = entityManager.createQuery(
                        TOPIC_SELECT
                                + " WHERE topic.id = :topicId AND topic.deletedAt IS NULL"
                                + TOPIC_GROUP_BY,
                        AdminTopicProjection.class
                )
                .setParameter("topicId", topicId);
        setModerationStatuses(query);
        return query
                .getResultStream()
                .findFirst();
    }

    private void setModerationStatuses(TypedQuery<AdminTopicProjection> query) {
        query.setParameter("pendingStatus", ModerationStatus.PENDING);
        query.setParameter("approvedStatus", ModerationStatus.APPROVED);
        query.setParameter("rejectedStatus", ModerationStatus.REJECTED);
    }

    private void appendPhase(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            AdminTopicQueryCriteria criteria
    ) {
        if (criteria.phase() == null) {
            return;
        }
        parameters.add(new QueryParameter("now", criteria.now()));
        if (criteria.phase() == TopicPhase.BEFORE_OPEN) {
            queryText.append(" AND topic.participationPeriod.startsAt > :now");
            return;
        }
        if (criteria.phase() == TopicPhase.OPEN) {
            queryText.append(" AND topic.participationPeriod.startsAt <= :now")
                    .append(" AND topic.participationPeriod.endsAt > :now");
            return;
        }
        queryText.append(" AND topic.participationPeriod.endsAt <= :now");
    }

    private void appendFilter(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            Object value,
            String clause,
            String parameterName
    ) {
        if (value == null) {
            return;
        }
        queryText.append(clause);
        parameters.add(new QueryParameter(parameterName, value));
    }

    private void appendOrder(StringBuilder queryText, AdminTopicQuerySort sort) {
        switch (sort) {
            case TOPIC_DATE_ASC -> queryText.append(
                    " ORDER BY topic.topicDate ASC, topic.id ASC");
            case CREATED_AT_DESC -> queryText.append(
                    " ORDER BY topic.createdAt DESC, topic.id DESC");
            case CREATED_AT_ASC -> queryText.append(
                    " ORDER BY topic.createdAt ASC, topic.id ASC");
            default -> queryText.append(
                    " ORDER BY topic.topicDate DESC, topic.id DESC");
        }
    }

    private record QueryParameter(String name, Object value) {
    }
}
