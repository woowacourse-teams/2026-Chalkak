package com.chalkak.backend.notification.infrastructure.persistence;

import com.chalkak.backend.notification.domain.Reminder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderJpaRepository extends JpaRepository<Reminder, UUID> {

    Slice<Reminder> findByDeletedAtIsNull(Pageable pageable);

    // 동시에 고쳐도 감사 로그의 변경 전 상태가 직전 변경 결과가 되도록 리마인더 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT reminder FROM Reminder reminder WHERE reminder.id = :reminderId")
    Optional<Reminder> findByIdForUpdate(@Param("reminderId") UUID reminderId);

    List<Reminder> findByDeletedAtIsNullOrderByIdAsc();

    Optional<Reminder> findByIdAndDeletedAtIsNull(UUID reminderId);

    // 정시 발행은 수정·삭제 중인 리마인더를 기다리지 않고 건너뛴 뒤 다음 확인 때 새 내용으로 발행한다.
    @Query(value = """
            SELECT * FROM reminders
            WHERE id = :reminderId AND deleted_at IS NULL
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    Optional<Reminder> findActiveForPublication(@Param("reminderId") UUID reminderId);
}
