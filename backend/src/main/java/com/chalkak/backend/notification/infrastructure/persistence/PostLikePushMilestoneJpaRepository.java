package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
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
}
