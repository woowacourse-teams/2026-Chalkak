package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.admin.repository.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.AdminPostQueryPage;
import com.chalkak.backend.admin.repository.AdminPostQueryRepository;
import com.chalkak.backend.admin.repository.AdminPostQuerySort;
import com.chalkak.backend.admin.repository.AdminPostSummaryProjection;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminPostQueryRepositoryImpl implements AdminPostQueryRepository {

    private static final String SUMMARY_SELECT = """
            SELECT new com.chalkak.backend.admin.repository.AdminPostSummaryProjection(
                post.id,
                post.title,
                post.moderationStatus,
                topic.id,
                topic.title,
                topic.topicDate,
                author.id,
                author.email,
                author.status,
                author.deletedAt,
                photo.id,
                photo.originalStorageKey,
                photo.thumbnailStorageKey,
                (SELECT COUNT(postLike)
                 FROM PostLike postLike
                 WHERE postLike.postId = post.id),
                post.createdAt,
                post.moderatedAt,
                post.deletedAt
            )
            FROM Post post
            JOIN post.topic topic
            JOIN post.author author
            JOIN post.photo photo
            """;

    private static final String DETAIL_QUERY = """
            SELECT new com.chalkak.backend.admin.repository.AdminPostDetailProjection(
                post.id,
                post.title,
                post.moderationStatus,
                post.createdAt,
                post.updatedAt,
                post.moderatedAt,
                moderationAudit.actorAdminId,
                moderationAudit.reason,
                post.deletedAt,
                author.id,
                author.email,
                author.status,
                author.deletedAt,
                topic.id,
                topic.title,
                topic.topicDate,
                topic.participationPeriod.startsAt,
                topic.participationPeriod.endsAt,
                topic.deletedAt,
                photo.id,
                photo.originalStorageKey,
                photo.thumbnailStorageKey,
                photo.metadata,
                photo.createdAt,
                photo.updatedAt,
                photo.deletedAt,
                upload.id,
                upload.status,
                upload.rejectionReason,
                upload.createdAt,
                upload.updatedAt,
                (SELECT COUNT(postLike)
                 FROM PostLike postLike
                 WHERE postLike.postId = post.id)
            )
            FROM Post post
            JOIN post.topic topic
            JOIN post.author author
            JOIN post.photo photo
            LEFT JOIN PostImageUpload upload ON upload.id = post.postImageUploadId
            LEFT JOIN AdminAuditLog moderationAudit
              ON moderationAudit.targetType = :postTargetType
             AND moderationAudit.targetId = post.id
             AND moderationAudit.action IN :moderationActions
            WHERE post.id = :postId
            """;

    private final EntityManager entityManager;

    @Override
    public AdminPostQueryPage findPosts(
            AdminPostQueryCriteria criteria,
            int page,
            int pageSize
    ) {
        StringBuilder queryText = new StringBuilder(SUMMARY_SELECT);
        List<QueryParameter> parameters = new ArrayList<>();
        appendFilters(queryText, parameters, criteria);
        appendOrder(queryText, criteria.sort());

        TypedQuery<AdminPostSummaryProjection> query = entityManager.createQuery(
                queryText.toString(),
                AdminPostSummaryProjection.class
        );
        parameters.forEach(parameter -> query.setParameter(parameter.name(), parameter.value()));
        query.setFirstResult((page - 1) * pageSize);
        query.setMaxResults(pageSize + 1);

        List<AdminPostSummaryProjection> rows = query.getResultList();
        boolean hasNext = rows.size() > pageSize;
        List<AdminPostSummaryProjection> posts = hasNext
                ? List.copyOf(rows.subList(0, pageSize))
                : List.copyOf(rows);

        return new AdminPostQueryPage(posts, page, pageSize, hasNext);
    }

    @Override
    public Optional<AdminPostDetailProjection> findPostById(UUID postId) {
        return entityManager.createQuery(DETAIL_QUERY, AdminPostDetailProjection.class)
                .setParameter("postId", postId)
                .setParameter("postTargetType", AdminTargetType.POST)
                .setParameter(
                        "moderationActions",
                        Set.of(AdminAction.POST_APPROVED, AdminAction.POST_REJECTED)
                )
                .getResultStream()
                .findFirst();
    }

    private void appendFilters(
            StringBuilder queryText,
            List<QueryParameter> parameters,
            AdminPostQueryCriteria criteria
    ) {
        queryText.append(" WHERE 1 = 1");
        appendFilter(
                queryText,
                parameters,
                criteria.status(),
                " AND post.moderationStatus = :status",
                "status"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.topicId(),
                " AND topic.id = :topicId",
                "topicId"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.topicDate(),
                " AND topic.topicDate = :topicDate",
                "topicDate"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.userId(),
                " AND author.id = :userId",
                "userId"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.createdAtFrom(),
                " AND post.createdAt >= :createdAtFrom",
                "createdAtFrom"
        );
        appendFilter(
                queryText,
                parameters,
                criteria.createdAtTo(),
                " AND post.createdAt <= :createdAtTo",
                "createdAtTo"
        );
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

    private void appendOrder(StringBuilder queryText, AdminPostQuerySort sort) {
        if (sort == AdminPostQuerySort.CREATED_AT_ASC) {
            queryText.append(" ORDER BY post.createdAt ASC, post.id ASC");
            return;
        }
        queryText.append(" ORDER BY post.createdAt DESC, post.id DESC");
    }

    private record QueryParameter(String name, Object value) {
    }
}
