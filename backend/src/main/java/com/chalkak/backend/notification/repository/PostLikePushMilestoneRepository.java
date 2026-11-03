package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostLikePushMilestoneRepository {

    PostLikePushMilestone save(PostLikePushMilestone milestone);

    Optional<Long> findMaxCountByPostId(UUID postId);

    List<UUID> findDuePublicationIds(Instant now, int limit);

    Optional<PostLikePushMilestone> findPendingPublicationForUpdate(
            UUID milestoneId,
            Instant now
    );

    Optional<PostLikePushMilestone> findForPushByIdAndUserId(UUID milestoneId, UUID userId);
}
