package com.chalkak.backend.admin.repository;

import com.chalkak.backend.admin.domain.PostMediaDeletionPlan;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostMediaDeletionPlanRepository {

    PostMediaDeletionPlan save(PostMediaDeletionPlan plan);

    Optional<PostMediaDeletionPlan> findByPostId(UUID postId);

    List<UUID> findDuePostIds(Instant dueAt, int limit);

    int markSucceededIfIncomplete(UUID postId, Instant completedAt);

    int markFailedIfIncomplete(
            UUID postId,
            String errorCode,
            Instant nextAttemptAt,
            Instant updatedAt
    );

    int retryFailedNow(UUID postId, Instant nextAttemptAt);
}
