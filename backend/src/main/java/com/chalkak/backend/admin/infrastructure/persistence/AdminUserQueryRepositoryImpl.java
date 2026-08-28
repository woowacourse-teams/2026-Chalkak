package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.repository.AdminUserDetailProjection;
import com.chalkak.backend.admin.repository.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.AdminUserQueryPage;
import com.chalkak.backend.admin.repository.AdminUserQueryRepository;
import com.chalkak.backend.admin.repository.AdminUserQuerySort;
import com.chalkak.backend.admin.repository.AdminUserQueryStatus;
import com.chalkak.backend.admin.repository.AdminUserSummaryProjection;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.user.domain.UserStatus;
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
public class AdminUserQueryRepositoryImpl implements AdminUserQueryRepository {

    private static final String SUMMARY_SELECT = """
            SELECT new com.chalkak.backend.admin.repository.AdminUserSummaryProjection(
                user.id,
                user.email,
                user.status,
                user.appVersion,
                socialAccount.provider,
                user.createdAt,
                user.updatedAt,
                user.deletedAt,
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :validatingStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :pendingStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :approvedStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :rejectedStatus)
            )
            FROM User user
            LEFT JOIN SocialAccount socialAccount ON socialAccount.user.id = user.id
            """;

    private static final String DETAIL_QUERY = """
            SELECT new com.chalkak.backend.admin.repository.AdminUserDetailProjection(
                user.id,
                user.email,
                user.status,
                user.appVersion,
                socialAccount.provider,
                user.signatureOriginalStorageKey,
                user.signatureThumbnailStorageKey,
                user.createdAt,
                user.updatedAt,
                user.deletedAt,
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :validatingStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :pendingStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :approvedStatus),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :rejectedStatus)
            )
            FROM User user
            LEFT JOIN SocialAccount socialAccount ON socialAccount.user.id = user.id
            WHERE user.id = :userId
            """;

    private final EntityManager entityManager;

    @Override
    public AdminUserQueryPage findUsers(
            AdminUserQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        StringBuilder queryText = new StringBuilder(SUMMARY_SELECT);
        List<QueryParameter> parameters = new ArrayList<>();
        appendFilters(queryText, parameters, criteria);
        appendOrder(queryText, criteria.sort());

        TypedQuery<AdminUserSummaryProjection> query = entityManager.createQuery(
                queryText.toString(),
                AdminUserSummaryProjection.class);
        setPostStatusParameters(query);
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);

        List<AdminUserSummaryProjection> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<AdminUserSummaryProjection> users = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);
        return new AdminUserQueryPage(users, page, pageSize, hasNext);
    }

    @Override
    public Optional<AdminUserDetailProjection> findUserById(UUID userId) {
        TypedQuery<AdminUserDetailProjection> query = entityManager.createQuery(
                DETAIL_QUERY,
                AdminUserDetailProjection.class);
        setPostStatusParameters(query);
        return query.setParameter("userId", userId)
                .getResultStream()
                .findFirst();
    }

    private void appendFilters(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            AdminUserQueryCriteria criteria
    ) {
        queryText.append(" WHERE 1 = 1");
        appendStatusFilter(queryText, parameters, criteria.status());
        if (criteria.email() == null) {
            return;
        }
        queryText.append(" AND LOCATE(LOWER(:email), LOWER(user.email)) > 0");
        parameters.add(new QueryParameter("email", criteria.email()));
    }

    private void appendStatusFilter(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            AdminUserQueryStatus status
    ) {
        if (status == null) {
            return;
        }
        if (status == AdminUserQueryStatus.WITHDRAWN) {
            queryText.append(" AND user.deletedAt IS NOT NULL");
            return;
        }
        queryText.append(" AND user.deletedAt IS NULL AND user.status = :userStatus");
        UserStatus userStatus = status == AdminUserQueryStatus.ACTIVE
                ? UserStatus.ACTIVE
                : UserStatus.BANNED;
        parameters.add(new QueryParameter("userStatus", userStatus));
    }

    private void appendOrder(StringBuilder queryText, AdminUserQuerySort sort) {
        if (sort == AdminUserQuerySort.CREATED_AT_ASC) {
            queryText.append(" ORDER BY user.createdAt ASC, user.id ASC");
            return;
        }
        queryText.append(" ORDER BY user.createdAt DESC, user.id DESC");
    }

    private void setPostStatusParameters(TypedQuery<?> query) {
        query.setParameter("validatingStatus", ModerationStatus.VALIDATING);
        query.setParameter("pendingStatus", ModerationStatus.PENDING);
        query.setParameter("approvedStatus", ModerationStatus.APPROVED);
        query.setParameter("rejectedStatus", ModerationStatus.REJECTED);
    }

    private record QueryParameter(String name, Object value) {
    }
}
