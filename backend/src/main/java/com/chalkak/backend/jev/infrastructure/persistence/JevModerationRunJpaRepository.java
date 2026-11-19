package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevModerationRun;
import com.chalkak.backend.jev.repository.JevJobTarget;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JevModerationRunJpaRepository extends JpaRepository<JevModerationRun, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT run FROM JevModerationRun run WHERE run.id = :id")
    Optional<JevModerationRun> findForUpdate(@Param("id") UUID id);

    @Query("SELECT new com.chalkak.backend.jev.repository.JevJobTarget(run.postId, run.preview) FROM JevModerationRun run WHERE run.id = :id")
    Optional<JevJobTarget> findTarget(@Param("id") UUID id);

    Optional<JevModerationRun> findByPostIdAndPreviewFalse(UUID postId);

    @Query(value = """
            SELECT run.id FROM jev_moderation_runs run
            JOIN posts post ON post.id = run.post_id
            JOIN users author ON author.id = post.user_id
            WHERE NOT run.preview AND run.applied_at IS NULL AND run.finished_at IS NOT NULL
              AND (post.moderation_status <> 'VALIDATING' OR post.deleted_at IS NOT NULL OR author.deleted_at IS NOT NULL)
            ORDER BY run.finished_at, run.id LIMIT 20
            """, nativeQuery = true)
    List<UUID> findUnappliedIds();

    @Query(value = """
            SELECT run.id FROM jev_moderation_runs run
            JOIN posts post ON post.id = run.post_id
            JOIN users author ON author.id = post.user_id
            WHERE run.finished_at IS NULL AND run.dispatch_at <= :now
              AND (run.preview OR post.moderation_status <> 'VALIDATING' OR post.deleted_at IS NOT NULL OR author.deleted_at IS NOT NULL)
            ORDER BY run.dispatch_at, run.id LIMIT 20
            """, nativeQuery = true)
    List<UUID> findDueIds(@Param("now") Instant now);
}
