package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminTopicConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "SUCCESS";
    private static final UUID FIRST_ADMIN_ID =
            UUID.fromString("0198fd22-0000-7000-8000-000000000001");
    private static final UUID SECOND_ADMIN_ID =
            UUID.fromString("0198fd22-0000-7000-8000-000000000002");
    private static final UUID UNKNOWN_ADMIN_ID =
            UUID.fromString("0198fd22-0000-7000-8000-000000000099");
    private static final LocalDate TOPIC_DATE = LocalDate.of(2099, 1, 2);
    private static final Instant STARTS_AT = Instant.parse("2099-01-01T15:00:00Z");
    private static final Instant ENDS_AT = Instant.parse("2099-01-02T15:00:00Z");

    @Autowired
    private AdminTopicCommandService adminTopicCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin(FIRST_ADMIN_ID, "first-topic-manager");
        insertAdmin(SECOND_ADMIN_ID, "second-topic-manager");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 날짜의 주제를 동시에 생성하면 하나만 저장하고 감사 로그도 하나만 남긴다")
    void createTopic_sameDateConcurrently_createsExactlyOnce() throws Exception {
        Callable<AdminTopicDetail> first = () -> adminTopicCommandService.createTopic(
                FIRST_ADMIN_ID, "첫 번째 주제", TOPIC_DATE, STARTS_AT, ENDS_AT);
        Callable<AdminTopicDetail> second = () -> adminTopicCommandService.createTopic(
                SECOND_ADMIN_ID, "두 번째 주제", TOPIC_DATE, STARTS_AT, ENDS_AT);

        List<CreateAttempt> attempts = runConcurrently(first, second);

        assertThat(attempts)
                .extracting(CreateAttempt::outcome)
                .containsExactlyInAnyOrder(SUCCESS, "BUSINESS_ERROR");
        assertThat(countTopics()).isEqualTo(1);
        assertThat(countAuditLogs()).isEqualTo(1);
    }

    @Test
    @DisplayName("감사 로그 관리자 외래키 저장이 실패하면 주제 생성도 롤백한다")
    void createTopic_auditActorForeignKeyFailure_rollsBackTopic() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        Throwable exception = catchThrowable(() -> transactionTemplate.executeWithoutResult(
                status -> adminTopicCommandService.createTopic(
                        UNKNOWN_ADMIN_ID,
                        "롤백 주제",
                        TOPIC_DATE,
                        STARTS_AT,
                        ENDS_AT
                )
        ));

        assertThat(exception).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(countTopics()).isZero();
        assertThat(countAuditLogs()).isZero();
    }

    private List<CreateAttempt> runConcurrently(
            Callable<AdminTopicDetail> first,
            Callable<AdminTopicDetail> second
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<CreateAttempt> firstResult = executor.submit(() -> attempt(barrier, first));
            Future<CreateAttempt> secondResult = executor.submit(() -> attempt(barrier, second));
            return List.of(
                    firstResult.get(10, TimeUnit.SECONDS),
                    secondResult.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private CreateAttempt attempt(
            CyclicBarrier barrier,
            Callable<AdminTopicDetail> action
    ) {
        try {
            barrier.await();
            return new CreateAttempt(SUCCESS, action.call());
        } catch (BaseException exception) {
            return new CreateAttempt(exception.getErrorCode().name(), null);
        } catch (Exception exception) {
            return new CreateAttempt(
                    "UNEXPECTED:" + exception.getClass().getSimpleName(),
                    null
            );
        }
    }

    private int countTopics() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM topics WHERE topic_date = ? AND deleted_at IS NULL",
                Integer.class,
                TOPIC_DATE
        );
    }

    private int countAuditLogs() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs
                WHERE target_type = CAST('TOPIC' AS admin_target_type)
                  AND after_state ->> 'topicDate' = ?
                """, Integer.class, TOPIC_DATE.toString());
    }

    private void insertAdmin(UUID id, String username) {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, ?, 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, username);
    }

    private void cleanUp() {
        jdbcTemplate.update("""
                DELETE FROM admin_audit_logs
                WHERE target_type = CAST('TOPIC' AS admin_target_type)
                  AND after_state ->> 'topicDate' = ?
                """, TOPIC_DATE.toString());
        jdbcTemplate.update("DELETE FROM topics WHERE topic_date = ?", TOPIC_DATE);
        jdbcTemplate.update(
                "DELETE FROM admins WHERE id IN (?, ?, ?)",
                FIRST_ADMIN_ID,
                SECOND_ADMIN_ID,
                UNKNOWN_ADMIN_ID
        );
    }

    private record CreateAttempt(
            String outcome,
            AdminTopicDetail result
    ) {
    }
}
