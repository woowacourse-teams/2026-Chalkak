package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.PostMediaDeletionPlan;
import com.chalkak.backend.admin.domain.PostMediaDeletionStatus;
import com.chalkak.backend.admin.repository.PostMediaDeletionPlanRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class PostMediaDeletionPlanRepositoryImpl
        implements PostMediaDeletionPlanRepository {

    private final PostMediaDeletionPlanJpaRepository jpaRepository;

    @Override
    public PostMediaDeletionPlan save(PostMediaDeletionPlan plan) {
        return jpaRepository.save(plan);
    }

    @Override
    public Optional<PostMediaDeletionPlan> findByPostId(UUID postId) {
        return jpaRepository.findByPostId(postId);
    }

    @Override
    public List<UUID> findDuePostIds(Instant dueAt, int limit) {
        return jpaRepository.findDuePostIds(
                PostMediaDeletionStatus.SUCCEEDED,
                dueAt,
                PageRequest.of(0, limit)
        );
    }

    @Override
    @Transactional
    public int markSucceededIfIncomplete(UUID postId, Instant completedAt) {
        return jpaRepository.markSucceededIfIncomplete(
                postId,
                PostMediaDeletionStatus.SUCCEEDED,
                completedAt
        );
    }

    @Override
    @Transactional
    public int markFailedIfIncomplete(
            UUID postId,
            String errorCode,
            Instant nextAttemptAt,
            Instant updatedAt
    ) {
        return jpaRepository.markFailedIfIncomplete(
                postId,
                PostMediaDeletionStatus.FAILED,
                PostMediaDeletionStatus.SUCCEEDED,
                errorCode,
                nextAttemptAt,
                updatedAt
        );
    }

    @Override
    @Transactional
    public int retryFailedNow(UUID postId, Instant nextAttemptAt) {
        return jpaRepository.retryFailedNow(
                postId,
                PostMediaDeletionStatus.FAILED,
                nextAttemptAt
        );
    }
}
