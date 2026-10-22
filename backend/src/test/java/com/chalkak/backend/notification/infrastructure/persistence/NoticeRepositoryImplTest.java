package com.chalkak.backend.notification.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.repository.DistributableNotice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
    @DisplayName("배포 대상은 예약 시각이 지났거나 정각인 미배포 공지만 예약 시각 순으로 조회한다")
    void findDistributableNotices_mixedNotices_returnsDueNoticesInOrder() {
        // Given
        UUID later = insertNotice(SCHEDULED_AT, false);
        UUID earlier = insertNotice(SCHEDULED_AT.minusSeconds(60), false);
        insertNotice(SCHEDULED_AT.plusNanos(1_000), false);
        insertNotice(SCHEDULED_AT.minusSeconds(120), true);

        // When & Then
        assertThat(targetIds(noticeRepository.findDistributableNotices(
                SCHEDULED_AT,
                null,
                10
        )))
                .containsExactly(earlier, later);
        assertThat(targetIds(noticeRepository.findDistributableNotices(
                SCHEDULED_AT.minusNanos(1_000),
                null,
                10
        ))).containsExactly(earlier);
        assertThat(targetIds(noticeRepository.findDistributableNotices(
                SCHEDULED_AT,
                null,
                1
        )))
                .containsExactly(earlier);
    }

    @Test
    @DisplayName("커서를 주면 그 공지 다음부터 이어서 조회하고 예약 시각이 같으면 ID 순으로 잇는다")
    void findDistributableNotices_withCursor_returnsNextNotices() {
        // Given
        UUID first = insertNotice(SCHEDULED_AT.minusSeconds(60), false);
        UUID secondA = insertNotice(SCHEDULED_AT, false);
        UUID secondB = insertNotice(SCHEDULED_AT, false);
        UUID third = insertNotice(SCHEDULED_AT.plusSeconds(1), false);
        List<UUID> seconds = orderedByDb(secondA, secondB);
        Instant now = SCHEDULED_AT.plusSeconds(60);
        List<DistributableNotice> all = noticeRepository.findDistributableNotices(
                now,
                null,
                10
        );
        DistributableNotice cursor = all.get(1);

        // When
        List<DistributableNotice> next = noticeRepository.findDistributableNotices(
                now,
                cursor,
                10
        );

        // Then
        assertThat(targetIds(all)).containsExactly(
                first,
                seconds.get(0),
                seconds.get(1),
                third
        );
        assertThat(targetIds(next)).containsExactly(seconds.get(1), third);
    }

    @Test
    @DisplayName("배포할 공지를 잠글 때 이미 배포했거나 예약 시각 전인 공지는 조회하지 않는다")
    void findDistributableForUpdate_notDue_returnsEmpty() {
        // Given
        UUID distributed = insertNotice(SCHEDULED_AT, true);
        UUID future = insertNotice(SCHEDULED_AT.plusSeconds(60), false);
        UUID due = insertNotice(SCHEDULED_AT, false);

        // When & Then
        assertThat(noticeRepository.findDistributableForUpdate(distributed, SCHEDULED_AT))
                .isEmpty();
        assertThat(noticeRepository.findDistributableForUpdate(future, SCHEDULED_AT)).isEmpty();
        assertThat(noticeRepository.findDistributableForUpdate(due, SCHEDULED_AT)).isPresent();
    }

    @Test
    @DisplayName("삭제된 공지는 배포 대상 조회와 배포 잠금 조회에서 제외한다")
    void findDistributableNotices_deletedNotice_excludes() {
        // Given
        UUID deleted = insertNotice(SCHEDULED_AT, false);
        UUID active = insertNotice(SCHEDULED_AT.plusSeconds(1), false);
        jdbcTemplate.update(
                "UPDATE notices SET deleted_at = ? WHERE id = ?",
                Timestamp.from(SCHEDULED_AT),
                deleted
        );
        Instant now = SCHEDULED_AT.plusSeconds(60);

        // When & Then
        assertThat(targetIds(noticeRepository.findDistributableNotices(
                now,
                null,
                10
        )))
                .containsExactly(active);
        assertThat(targetIds(noticeRepository.findDistributableNotices(
                now,
                new DistributableNotice(UUID.randomUUID(), SCHEDULED_AT.minusSeconds(1)),
                10
        )))
                .containsExactly(active);
        assertThat(noticeRepository.findDistributableForUpdate(deleted, now)).isEmpty();
    }

    private UUID insertNotice(Instant scheduledAt, boolean distributed) {
        Timestamp distributedAt = null;
        if (distributed) {
            distributedAt = Timestamp.from(scheduledAt);
        }
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO notices (title, body, scheduled_at, distributed_at, created_at,
                            updated_at)
                        VALUES ('공지', '본문', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        RETURNING id
                        """,
                UUID.class,
                Timestamp.from(scheduledAt),
                distributedAt
        );
    }

    private List<UUID> targetIds(List<DistributableNotice> targets) {
        return targets.stream().map(DistributableNotice::noticeId).toList();
    }

    // UUID 대소 비교는 Java와 PostgreSQL이 다르므로 DB가 정렬한 순서를 기대값으로 쓴다.
    private List<UUID> orderedByDb(UUID a, UUID b) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM notices WHERE id IN (?, ?) ORDER BY id",
                UUID.class,
                a,
                b
        );
    }
}
