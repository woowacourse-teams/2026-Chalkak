package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.repository.AdminAuditLogQueryCriteria;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryPage;
import com.chalkak.backend.admin.repository.AdminAuditLogQueryRepository;
import com.chalkak.backend.admin.repository.AdminAuditLogQuerySort;
import com.chalkak.backend.admin.repository.AdminAuditLogSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminAuditLogQueryRepositoryImpl implements AdminAuditLogQueryRepository {

    private static final String SELECT = """
            SELECT new com.chalkak.backend.admin.repository.AdminAuditLogSummaryProjection(
                auditLog.id,
                auditLog.actorAdminId,
                admin.username,
                auditLog.action,
                auditLog.targetType,
                auditLog.targetId,
                auditLog.reason,
                auditLog.beforeState,
                auditLog.afterState,
                auditLog.occurredAt,
                auditLog.requestId
            )
            FROM AdminAuditLog auditLog
            JOIN Admin admin ON admin.id = auditLog.actorAdminId
            """;

    private final EntityManager entityManager;

    @Override
    public AdminAuditLogQueryPage findAuditLogs(
            AdminAuditLogQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        StringBuilder queryText = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<QueryParameter> parameters = new ArrayList<>();
        appendFilter(queryText, parameters, criteria.adminId(),
                " AND auditLog.actorAdminId = :adminId", "adminId");
        appendFilter(queryText, parameters, criteria.action(),
                " AND auditLog.action = :action", "action");
        appendFilter(queryText, parameters, criteria.targetType(),
                " AND auditLog.targetType = :targetType", "targetType");
        appendFilter(queryText, parameters, criteria.targetId(),
                " AND auditLog.targetId = :targetId", "targetId");
        appendFilter(queryText, parameters, criteria.occurredFrom(),
                " AND auditLog.occurredAt >= :occurredFrom", "occurredFrom");
        appendFilter(queryText, parameters, criteria.occurredTo(),
                " AND auditLog.occurredAt <= :occurredTo", "occurredTo");
        appendOrder(queryText, criteria.sort());

        TypedQuery<AdminAuditLogSummaryProjection> query = entityManager.createQuery(
                queryText.toString(), AdminAuditLogSummaryProjection.class
        );
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);
        List<AdminAuditLogSummaryProjection> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<AdminAuditLogSummaryProjection> auditLogs = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);
        return new AdminAuditLogQueryPage(auditLogs, page, pageSize, hasNext);
    }

    private void appendFilter(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            Object value,
            String clause,
            String parameterName
    ) {
        if (value != null) {
            queryText.append(clause);
            parameters.add(new QueryParameter(parameterName, value));
        }
    }

    private void appendOrder(StringBuilder queryText, AdminAuditLogQuerySort sort) {
        if (sort == AdminAuditLogQuerySort.OCCURRED_AT_ASC) {
            queryText.append(" ORDER BY auditLog.occurredAt ASC, auditLog.id ASC");
            return;
        }
        queryText.append(" ORDER BY auditLog.occurredAt DESC, auditLog.id DESC");
    }

    private record QueryParameter(String name, Object value) {
    }
}
