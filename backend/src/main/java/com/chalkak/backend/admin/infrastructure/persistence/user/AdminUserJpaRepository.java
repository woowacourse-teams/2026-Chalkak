package com.chalkak.backend.admin.infrastructure.persistence.user;

import com.chalkak.backend.admin.repository.user.AdminUserDetailProjection;
import com.chalkak.backend.admin.repository.user.AdminUserQueryCriteria;
import com.chalkak.backend.admin.repository.user.AdminUserSummaryProjection;
import com.chalkak.backend.user.domain.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminUserJpaRepository extends JpaRepository<User, UUID> {

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.user.AdminUserSummaryProjection(
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
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).PENDING}),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).APPROVED}),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).REJECTED})
            )
            FROM User user
            LEFT JOIN SocialAccount socialAccount ON socialAccount.user.id = user.id
            WHERE (:#{#criteria.status() == null} = true
                   OR (:#{#criteria.status()?.name()} = 'WITHDRAWN' AND user.deletedAt IS NOT NULL)
                   OR (:#{#criteria.status()?.name()} = 'ACTIVE' AND user.deletedAt IS NULL
                       AND user.status = :#{T(com.chalkak.backend.user.domain.UserStatus).ACTIVE})
                   OR (:#{#criteria.status()?.name()} = 'BANNED' AND user.deletedAt IS NULL
                       AND user.status = :#{T(com.chalkak.backend.user.domain.UserStatus).BANNED}))
              AND (:#{#criteria.email() == null} = true
                   OR LOCATE(LOWER(:#{#criteria.email()}), LOWER(user.email)) > 0)
            """)
    Slice<AdminUserSummaryProjection> findUsers(
            @Param("criteria") AdminUserQueryCriteria criteria,
            Pageable pageable
    );

    @Query("""
            SELECT new com.chalkak.backend.admin.repository.user.AdminUserDetailProjection(
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
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).PENDING}),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).APPROVED}),
                (SELECT COUNT(post)
                 FROM Post post
                 WHERE post.author.id = user.id
                   AND post.moderationStatus = :#{T(com.chalkak.backend.post.domain.ModerationStatus).REJECTED})
            )
            FROM User user
            LEFT JOIN SocialAccount socialAccount ON socialAccount.user.id = user.id
            WHERE user.id = :id
            """)
    List<AdminUserDetailProjection> findUserDetail(
            @Param("id") UUID id,
            Pageable pageable
    );
}
