package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.DistributableNotice;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoticeJpaRepository extends JpaRepository<Notice, UUID> {

    Slice<Notice> findSliceBy(Pageable pageable);

    // 배포 처리와 겹치지 않도록 공지 행을 잠근다. 배포 중이면 끝날 때까지 기다린 뒤 배포 결과를 읽는다.
    // 잠금이 없으면 수정이 배포 전 상태를 읽은 채 모든 컬럼을 UPDATE해, 이미 배포한 공지의 배포 시각을 NULL로 되돌린다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT notice FROM Notice notice WHERE notice.id = :noticeId")
    Optional<Notice> findByIdForUpdate(@Param("noticeId") UUID noticeId);

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
