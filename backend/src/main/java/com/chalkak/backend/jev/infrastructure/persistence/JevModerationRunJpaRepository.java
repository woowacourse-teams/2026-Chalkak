package com.chalkak.backend.jev.infrastructure.persistence;

import com.chalkak.backend.jev.domain.JevModerationRun;
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

    @Query(value = """
            SELECT id FROM jev_moderation_runs
            WHERE finished_at IS NULL AND dispatch_at <= :now
            ORDER BY dispatch_at, id LIMIT 20
            """, nativeQuery = true)
    List<UUID> findDueIds(@Param("now") Instant now);
}
