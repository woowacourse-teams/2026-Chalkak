package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeJpaRepository extends JpaRepository<Notice, UUID> {

    Slice<Notice> findSliceBy(Pageable pageable);

    @Query(value = """
            SELECT id FROM notices
            WHERE status = 'SCHEDULED' AND scheduled_at <= :now
            ORDER BY scheduled_at, id
            LIMIT :limit
            """, nativeQuery = true)
    List<UUID> findDistributableIds(@Param("now") Instant now, @Param("limit") int limit);

    @Query(value = """
            SELECT * FROM notices
            WHERE id = :noticeId AND status = 'SCHEDULED' AND scheduled_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notice> findDistributableForUpdate(
            @Param("noticeId") UUID noticeId,
            @Param("now") Instant now
    );
}
