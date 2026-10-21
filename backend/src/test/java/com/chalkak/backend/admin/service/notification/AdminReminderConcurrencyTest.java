package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminReminderConcurrencyTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000732");

    @Autowired
    private AdminReminderService reminderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'reminder-concurrency-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    @Test
    @DisplayName("같은 리마인더를 동시에 수정하면 직전 변경 값을 감사 로그의 변경 전 값으로 기록한다")
    void updateReminder_sameReminderConcurrently_preservesAuditChain() throws Exception {
        // Given
        Instant scheduledAt = Instant.now().plus(Duration.ofHours(1));
        UUID reminderId = reminderService.createReminder(
                ADMIN_ID,
                "처음 제목",
                "본문",
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                scheduledAt
        ).reminderId();
        CyclicBarrier barrier = new CyclicBarrier(2);

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> updateAfterBarrier(
                    reminderId,
                    "첫째 제목",
                    barrier
            ));
            Future<?> second = executor.submit(() -> updateAfterBarrier(
                    reminderId,
                    "둘째 제목",
                    barrier
            ));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        // Then
        List<Map<String, Object>> audits = jdbcTemplate.queryForList(
                """
                        SELECT before_state ->> 'title' AS before_title, after_state ->> 'title' AS after_title
                        FROM admin_audit_logs WHERE target_id = ? AND action = 'REMINDER_UPDATED'
                        ORDER BY id
                        """,
                reminderId);
        assertThat(audits).hasSize(2);
        assertThat(audits.getFirst()).containsEntry("before_title", "처음 제목");
        assertThat(audits.get(1).get("before_title"))
                .isEqualTo(audits.getFirst().get("after_title"));
        assertThat(audits.get(1).get("after_title")).isEqualTo(jdbcTemplate.queryForObject(
                "SELECT title FROM reminders WHERE id = ?",
                String.class,
                reminderId
        ));
    }

    private void updateAfterBarrier(
            UUID reminderId,
            String title,
            CyclicBarrier barrier
    ) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("두 수정 요청을 동시에 시작하지 못했습니다.", exception);
        }
        reminderService.updateReminder(
                reminderId,
                ADMIN_ID,
                title,
                "본문",
                ReminderRepeat.DAILY,
                ReminderTarget.ALL,
                Instant.now().plus(Duration.ofHours(1))
        );
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }
}
