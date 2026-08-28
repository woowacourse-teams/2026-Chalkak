package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.repository.AdminTopicQueryCriteria;
import com.chalkak.backend.admin.repository.AdminTopicQueryPage;
import com.chalkak.backend.admin.repository.AdminTopicQueryRepository;
import com.chalkak.backend.admin.repository.AdminTopicQuerySort;
import com.chalkak.backend.topic.domain.Topic;
import com.chalkak.backend.topic.domain.TopicPhase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminTopicQueryRepositoryImpl implements AdminTopicQueryRepository {

    private final EntityManager entityManager;

    @Override
    public AdminTopicQueryPage findTopics(
            AdminTopicQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        StringBuilder queryText = new StringBuilder(
                "SELECT topic FROM Topic topic WHERE topic.deletedAt IS NULL"
        );
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
        appendOrder(queryText, criteria.sort());

        TypedQuery<Topic> query = entityManager.createQuery(queryText.toString(), Topic.class);
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);
        List<Topic> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<Topic> topics = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);
        return new AdminTopicQueryPage(topics, page, pageSize, hasNext);
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
