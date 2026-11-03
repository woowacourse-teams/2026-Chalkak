package com.chalkak.backend.notification.repository;

import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import java.util.Optional;
import java.util.UUID;

public interface PostLikePushMilestoneRepository {

    PostLikePushMilestone save(PostLikePushMilestone milestone);

    Optional<Long> findMaxCountByPostId(UUID postId);
}
