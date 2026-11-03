package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikePushMilestoneJpaRepository
        extends
            JpaRepository<PostLikePushMilestone, UUID> {

    @Query("""
            SELECT MAX(milestone.milestoneCount)
            FROM PostLikePushMilestone milestone
            WHERE milestone.postId = :postId
            """)
    Optional<Long> findMaxCountByPostId(@Param("postId") UUID postId);

    @Query(value = """
            SELECT id FROM post_like_push_milestones
            WHERE sqs_publish_status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDuePublicationIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM post_like_push_milestones
            WHERE id = :milestoneId AND sqs_publish_status = 'PENDING'
                AND next_attempt_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<PostLikePushMilestone> findPendingPublicationForUpdate(
            @Param("milestoneId") UUID milestoneId,
            @Param("now") Instant now
    );

    Optional<PostLikePushMilestone> findByIdAndUserId(UUID milestoneId, UUID userId);
}
