package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class NoticeRepositoryImplTest extends IntegrationTestSupport {

    private static final Instant SCHEDULED_AT = Instant.parse("2026-10-13T00:00:00Z");

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("배포 대상은 예약 시각이 지났거나 정각인 예약 상태 공지만 예약 시각 순으로 조회한다")
    void findDistributableIds_mixedNotices_returnsDueScheduledNoticesInOrder() {
        // Given
        UUID later = insertNotice(SCHEDULED_AT, "SCHEDULED");
        UUID earlier = insertNotice(SCHEDULED_AT.minusSeconds(60), "SCHEDULED");
        insertNotice(SCHEDULED_AT.plusNanos(1_000), "SCHEDULED");
        insertNotice(SCHEDULED_AT.minusSeconds(120), "DISTRIBUTED");

        // When & Then
        assertThat(noticeRepository.findDistributableIds(SCHEDULED_AT, 10)).containsExactly(earlier,
                later);
        assertThat(noticeRepository.findDistributableIds(SCHEDULED_AT.minusNanos(1_000), 10))
                .containsExactly(earlier);
        assertThat(noticeRepository.findDistributableIds(SCHEDULED_AT, 1)).containsExactly(earlier);
    }

    @Test
    @DisplayName("배포할 공지를 잠글 때 이미 배포했거나 예약 시각 전인 공지는 조회하지 않는다")
    void findDistributableForUpdate_notDue_returnsEmpty() {
        // Given
        UUID distributed = insertNotice(SCHEDULED_AT, "DISTRIBUTED");
        UUID future = insertNotice(SCHEDULED_AT.plusSeconds(60), "SCHEDULED");
        UUID due = insertNotice(SCHEDULED_AT, "SCHEDULED");

        // When & Then
        assertThat(noticeRepository.findDistributableForUpdate(distributed, SCHEDULED_AT))
                .isEmpty();
        assertThat(noticeRepository.findDistributableForUpdate(future, SCHEDULED_AT)).isEmpty();
        assertThat(noticeRepository.findDistributableForUpdate(due, SCHEDULED_AT)).isPresent();
    }

    private UUID insertNotice(Instant scheduledAt, String status) {
        Timestamp distributedAt = null;
        if (status.equals("DISTRIBUTED")) {
            distributedAt = Timestamp.from(scheduledAt);
        }
        return jdbcTemplate.queryForObject("""
                INSERT INTO notices (title, body, scheduled_at, status, distributed_at,
                    created_at, updated_at)
                VALUES ('공지', '본문', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, Timestamp.from(scheduledAt), status, distributedAt);
    }
}
