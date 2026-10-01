package com.chalkak.backend.admin.infrastructure.persistence.post;

import com.chalkak.backend.admin.repository.post.AdminPostDetailProjection;
import com.chalkak.backend.admin.repository.post.AdminPostQueryCriteria;
import com.chalkak.backend.admin.repository.post.AdminPostSummaryProjection;
import com.chalkak.backend.post.domain.Post;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminPostJpaRepository extends JpaRepository<Post, UUID> {

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.post.AdminPostSummaryProjection(
                post.id,
                post.title.value,
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
            WHERE post.moderationStatus <> :#{T(com.chalkak.backend.post.domain.ModerationStatus).VALIDATING}
              AND (:#{#criteria.status() == null} = true OR post.moderationStatus = :#{#criteria.status()})
              AND (:#{#criteria.topicId() == null} = true OR topic.id = :#{#criteria.topicId()})
              AND (:#{#criteria.topicDate() == null} = true OR topic.topicDate = :#{#criteria.topicDate()})
              AND (:#{#criteria.userId() == null} = true OR author.id = :#{#criteria.userId()})
              AND (:#{#criteria.createdAtFrom() == null} = true OR post.createdAt >= :#{#criteria.createdAtFrom()})
              AND (:#{#criteria.createdAtTo() == null} = true OR post.createdAt <= :#{#criteria.createdAtTo()})
            """)
    Slice<AdminPostSummaryProjection> findPosts(
            @Param("criteria") AdminPostQueryCriteria criteria,
            Pageable pageable
    );

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.post.AdminPostDetailProjection(
                post.id,
                post.title.value,
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
              ON moderationAudit.targetType = :#{T(com.chalkak.backend.admin.domain.AdminTargetType).POST}
             AND moderationAudit.targetId = post.id
             AND moderationAudit.action IN (:#{T(com.chalkak.backend.admin.domain.AdminAction).POST_APPROVED}, :#{T(com.chalkak.backend.admin.domain.AdminAction).POST_REJECTED})
            WHERE post.id = :id
              AND post.moderationStatus <> :#{T(com.chalkak.backend.post.domain.ModerationStatus).VALIDATING}
            """)
    List<AdminPostDetailProjection> findPostDetail(
            @Param("id") UUID id,
            Pageable pageable
    );
}
