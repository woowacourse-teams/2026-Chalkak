package com.chalkak.backend.aireview.infrastructure.persistence;

import com.chalkak.backend.aireview.domain.AiPromptType;
import com.chalkak.backend.aireview.domain.AiPromptVersion;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiPromptVersionJpaRepository extends JpaRepository<AiPromptVersion, UUID> {

    /**
     * 아직 없는 다음 버전 행까지 덮도록 행이 아니라 용도 키를 어드바이저리 잠금으로 건다.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(CAST(:lockKey AS text)))",
            nativeQuery = true)
    void lockByKey(@Param("lockKey") String lockKey);

    @Query("SELECT COALESCE(MAX(promptVersion.version), 0) FROM AiPromptVersion promptVersion "
            + "WHERE promptVersion.type = :type")
    int findMaxVersionByType(@Param("type") AiPromptType type);

    Slice<AiPromptVersion> findByType(AiPromptType type, Pageable pageable);
}
