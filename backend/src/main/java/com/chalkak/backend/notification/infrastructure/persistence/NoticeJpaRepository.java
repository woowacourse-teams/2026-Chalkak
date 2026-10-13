package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.DistributableNotice;
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

    @Query("""
            SELECT new com.chalkak.backend.notification.repository.DistributableNotice(
                notice.id, notice.scheduledAt)
            FROM Notice notice
            WHERE notice.distributedAt IS NULL AND notice.scheduledAt <= :now
            ORDER BY notice.scheduledAt, notice.id
            """)
    List<DistributableNotice> findDistributableNotices(
            @Param("now") Instant now,
            Pageable pageable
    );

    // 직전 묶음의 마지막 공지(예약 시각, ID) 다음부터 이어서 조회한다.
    @Query("""
            SELECT new com.chalkak.backend.notification.repository.DistributableNotice(
                notice.id, notice.scheduledAt)
            FROM Notice notice
            WHERE notice.distributedAt IS NULL AND notice.scheduledAt <= :now
                AND (notice.scheduledAt > :afterScheduledAt
                    OR (notice.scheduledAt = :afterScheduledAt AND notice.id > :afterId))
            ORDER BY notice.scheduledAt, notice.id
            """)
    List<DistributableNotice> findDistributableNoticesAfter(
            @Param("now") Instant now,
            @Param("afterScheduledAt") Instant afterScheduledAt,
            @Param("afterId") UUID afterId,
            Pageable pageable
    );

    @Query(value = """
            SELECT * FROM notices
            WHERE id = :noticeId AND distributed_at IS NULL AND scheduled_at <= :now
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Notice> findDistributableForUpdate(
            @Param("noticeId") UUID noticeId,
            @Param("now") Instant now
    );
}
