package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.TopicPushSchedule;
import com.chalkak.backend.notification.repository.TopicPushScheduleRepository;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalTime;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminTopicPushScheduleConcurrencyTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000902");

    @Autowired
    private AdminTopicPushScheduleService scheduleService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TopicPushScheduleRepository scheduleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        clean();
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'push-schedule-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        clean();
    }

    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }

    @Test
    @DisplayName("같은 시각의 동시 등록은 한 건만 커밋하고 나머지는 중복 오류로 거절한다")
    void createSchedule_sameTimeConcurrently_persistsExactlyOnce() throws Exception {
        // Given
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<String> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // When
            Future<String> first = executor.submit(() -> createAfterBarrier(barrier));
            Future<String> second = executor.submit(() -> createAfterBarrier(barrier));
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        // Then
        assertThat(results).containsExactlyInAnyOrder("CREATED", "DUPLICATE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_push_schedules", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE action = 'TOPIC_PUSH_SCHEDULE_CREATED'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("두 요청의 사전 조회가 모두 통과해도 DB 제약으로 한 건만 저장한다")
    void saveAndFlush_bothPrechecksPass_persistsExactlyOnce() throws Exception {
        // Given
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<String> results;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            // When
            Future<String> first = executor.submit(() -> saveAfterPrecheck(barrier));
            Future<String> second = executor.submit(() -> saveAfterPrecheck(barrier));
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        // Then
        assertThat(results).containsExactlyInAnyOrder("CREATED", "DUPLICATE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_push_schedules", Integer.class)).isEqualTo(1);
    }

    private String saveAfterPrecheck(CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                LocalTime sendTime = LocalTime.of(9, 0);
                assertThat(scheduleRepository.existsBySendTime(sendTime)).isFalse();
                awaitPrechecks(barrier);
                scheduleRepository.saveAndFlush(new TopicPushSchedule(sendTime));
            });
            return "CREATED";
        } catch (BusinessException exception) {
            assertThat(exception.getMessage()).isEqualTo("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
            return "DUPLICATE";
        }
    }

    private void awaitPrechecks(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("두 요청의 사전 조회를 기다리지 못했습니다.", exception);
        }
    }

    private String createAfterBarrier(CyclicBarrier barrier) throws Exception {
        barrier.await(5, TimeUnit.SECONDS);
        try {
            scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0));
            return "CREATED";
        } catch (BusinessException exception) {
            assertThat(exception.getMessage()).isEqualTo("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
            return "DUPLICATE";
        }
    }

    @Test
    @DisplayName("서로 다른 설정이 같은 시각으로 동시 수정되어도 한 건만 커밋한다")
    void updateSchedule_bothPrechecksPass_updatesExactlyOnce() throws Exception {
        // Given
        UUID firstId = scheduleService.createSchedule(ADMIN_ID, LocalTime.of(8, 0)).scheduleId();
        UUID secondId = scheduleService.createSchedule(ADMIN_ID, LocalTime.of(18, 0)).scheduleId();
        CyclicBarrier barrier = new CyclicBarrier(2);
        List<String> results;

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> updateAfterPrecheck(firstId, barrier));
            Future<String> second = executor.submit(() -> updateAfterPrecheck(secondId, barrier));
            results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }

        // Then
        assertThat(results).containsExactlyInAnyOrder("UPDATED", "DUPLICATE");
        assertThat(scheduleService.getSchedules()).hasSize(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_push_schedules WHERE send_time = '09:00:00'",
                Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topic_push_schedules WHERE send_time IN ('08:00:00', '18:00:00')",
                Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("같은 설정을 동시에 수정하면 직전 변경 값을 감사 로그의 변경 전 값으로 기록한다")
    void updateSchedule_sameScheduleConcurrently_preservesAuditChain() throws Exception {
        // Given
        UUID scheduleId = scheduleService.createSchedule(ADMIN_ID, LocalTime.of(9, 0)).scheduleId();
        CyclicBarrier barrier = new CyclicBarrier(2);

        // When
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor
                    .submit(() -> updateAfterBarrier(scheduleId, LocalTime.of(10, 0), barrier));
            Future<?> second = executor
                    .submit(() -> updateAfterBarrier(scheduleId, LocalTime.of(11, 0), barrier));
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        // Then
        List<java.util.Map<String, Object>> audits = jdbcTemplate.queryForList(
                """
                        SELECT before_state ->> 'sendTime' AS before_time, after_state ->> 'sendTime' AS after_time
                        FROM admin_audit_logs WHERE target_id = ? AND action = 'TOPIC_PUSH_SCHEDULE_UPDATED'
                        ORDER BY id
                        """,
                scheduleId);
        assertThat(audits).hasSize(2);
        assertThat(audits.getFirst()).containsEntry("before_time", "09:00:00");
        assertThat(audits.get(1).get("before_time")).isEqualTo(audits.getFirst().get("after_time"));
        assertThat(audits.get(1).get("after_time")).isEqualTo(
                jdbcTemplate.queryForObject(
                        "SELECT to_char(send_time, 'HH24:MI:SS') FROM topic_push_schedules WHERE id = ?",
                        String.class, scheduleId));
    }

    private void updateAfterBarrier(UUID scheduleId, LocalTime sendTime, CyclicBarrier barrier) {
        awaitPrechecks(barrier);
        scheduleService.updateSchedule(scheduleId, ADMIN_ID, sendTime);
    }

    private String updateAfterPrecheck(UUID scheduleId, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                TopicPushSchedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
                LocalTime sendTime = LocalTime.of(9, 0);
                assertThat(scheduleRepository.existsBySendTime(sendTime)).isFalse();
                awaitPrechecks(barrier);
                schedule.updateSendTime(sendTime);
                scheduleRepository.saveAndFlush(schedule);
            });
            return "UPDATED";
        } catch (BusinessException exception) {
            assertThat(exception.getMessage()).isEqualTo("해당 시각의 주제 푸시 설정이 이미 존재합니다.");
            return "DUPLICATE";
        }
    }
}
