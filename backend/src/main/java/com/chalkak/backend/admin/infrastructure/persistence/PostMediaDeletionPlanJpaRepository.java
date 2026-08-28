package com.chalkak.backend.admin.infrastructure.persistence;

import com.chalkak.backend.admin.domain.PostMediaDeletionPlan;
import com.chalkak.backend.admin.domain.PostMediaDeletionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostMediaDeletionPlanJpaRepository
        extends JpaRepository<PostMediaDeletionPlan, UUID> {

    Optional<PostMediaDeletionPlan> findByPostId(UUID postId);

    @Query("""
            SELECT plan.postId
            FROM PostMediaDeletionPlan plan
            WHERE plan.status <> :succeededStatus
              AND plan.nextAttemptAt <= :dueAt
            ORDER BY plan.nextAttemptAt ASC, plan.id ASC
            """)
    List<UUID> findDuePostIds(
            @Param("succeededStatus") PostMediaDeletionStatus succeededStatus,
            @Param("dueAt") Instant dueAt,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostMediaDeletionPlan plan
            SET plan.status = :succeededStatus,
                plan.attemptCount = plan.attemptCount + 1,
                plan.lastErrorCode = NULL,
                plan.nextAttemptAt = NULL,
                plan.completedAt = :completedAt,
                plan.updatedAt = :completedAt
            WHERE plan.postId = :postId
              AND plan.status <> :succeededStatus
            """)
    int markSucceededIfIncomplete(
            @Param("postId") UUID postId,
            @Param("succeededStatus") PostMediaDeletionStatus succeededStatus,
            @Param("completedAt") Instant completedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostMediaDeletionPlan plan
            SET plan.status = :failedStatus,
                plan.attemptCount = plan.attemptCount + 1,
                plan.lastErrorCode = :errorCode,
                plan.nextAttemptAt = :nextAttemptAt,
                plan.completedAt = NULL,
                plan.updatedAt = :updatedAt
            WHERE plan.postId = :postId
              AND plan.status <> :succeededStatus
            """)
    int markFailedIfIncomplete(
            @Param("postId") UUID postId,
            @Param("failedStatus") PostMediaDeletionStatus failedStatus,
            @Param("succeededStatus") PostMediaDeletionStatus succeededStatus,
            @Param("errorCode") String errorCode,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("updatedAt") Instant updatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PostMediaDeletionPlan plan
            SET plan.nextAttemptAt = :nextAttemptAt,
                plan.updatedAt = CURRENT_TIMESTAMP
            WHERE plan.postId = :postId
              AND plan.status = :failedStatus
            """)
    int retryFailedNow(
            @Param("postId") UUID postId,
            @Param("failedStatus") PostMediaDeletionStatus failedStatus,
            @Param("nextAttemptAt") Instant nextAttemptAt
    );
}
