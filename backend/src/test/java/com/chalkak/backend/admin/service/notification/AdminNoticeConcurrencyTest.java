package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.notification.repository.NoticeRepository;
import com.chalkak.backend.notification.repository.NotificationRepository;
import com.chalkak.backend.notification.service.NoticeDistributionService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 공지 수정·삭제와 배포가 같은 공지 행 잠금으로 직렬화되는지 검증한다. */
class AdminNoticeConcurrencyTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000612");

    @Autowired
    private AdminNoticeService noticeService;

    @Autowired
    private NoticeDistributionService distributionService;

    @Autowired
    private NoticeRepository noticeRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID noticeId;
    // 공용 스레드 풀은 CPU가 적으면 스레드가 하나뿐이라, 잠금을 쥔 작업과 기다리는 작업이 동시에 실행되도록 전용 풀을 쓴다.
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        clean();
        noticeId = jdbcTemplate.queryForObject("""
                INSERT INTO notices (title, body, scheduled_at, created_at, updated_at)
                VALUES ('서비스 점검 안내', '점검합니다.', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, Timestamp.from(Instant.now().minusSeconds(60)));
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        clean();
    }

    @Test
    @DisplayName("배포가 먼저 공지를 잠그면 수정은 배포 커밋을 기다린 뒤 거절된다")
    void updateNotice_distributionLocksFirst_rejectsAfterDistribution() throws Exception {
        // Given
        CountDownLatch distributionLocked = new CountDownLatch(1);
        CountDownLatch distributionCanCommit = new CountDownLatch(1);
        CompletableFuture<Void> distribution = CompletableFuture.runAsync(
                () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Instant now = Instant.now();
                    Notice notice = noticeRepository.findDistributableForUpdate(noticeId, now)
                            .orElseThrow();
                    distributionLocked.countDown();
                    await(distributionCanCommit);
                    notificationRepository.createNoticeNotifications(notice, now);
                    notice.distribute(now);
                }), executor);
        await(distributionLocked);

        // When
        CompletableFuture<AdminNoticeResult> update = CompletableFuture.supplyAsync(
                () -> noticeService.updateNotice(noticeId, ADMIN_ID, "점검 일정 변경", "점검합니다.",
                        Instant.now()),
                executor);
        try {
            awaitLockWaiting();
            assertThat(update.isDone()).isFalse();
        } finally {
            distributionCanCommit.countDown();
        }
        distribution.get(5, TimeUnit.SECONDS);

        // Then
        assertThatThrownBy(() -> update.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(BusinessException.class)
                .hasMessage("배포된 공지는 수정할 수 없습니다.");
        assertThat(jdbcTemplate.queryForObject("SELECT title FROM notices", String.class))
                .isEqualTo("서비스 점검 안내");
    }

    // 수정 서비스 대신 수정이 쓰는 잠금 조회만 호출해, 수정이 잡는 공지 행 잠금을 재현한다.
    @Test
    @DisplayName("수정이 공지를 잠그고 있으면 배포는 그 공지를 건너뛰고 다음 확인 때 배포한다")
    void distributeNotices_updateLocksFirst_skipsLockedNotice() throws Exception {
        // Given
        CountDownLatch updateLocked = new CountDownLatch(1);
        CountDownLatch updateCanCommit = new CountDownLatch(1);
        CompletableFuture<Void> update = CompletableFuture.runAsync(
                () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    noticeRepository.findByIdForUpdate(noticeId).orElseThrow();
                    updateLocked.countDown();
                    await(updateCanCommit);
                }), executor);
        await(updateLocked);

        // When
        try {
            distributionService.distributeNotices();
        } finally {
            updateCanCommit.countDown();
        }
        update.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT distributed_at IS NULL FROM notices", Boolean.class)).isTrue();
        distributionService.distributeNotices();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT distributed_at IS NOT NULL FROM notices", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("배포가 먼저 공지를 잠그면 삭제는 배포 커밋을 기다린 뒤 배포된 공지를 삭제한다")
    void deleteNotice_distributionLocksFirst_deletesDistributedNotice() throws Exception {
        // Given
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'notice-concurrency-manager', 'test-password', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        CountDownLatch distributionLocked = new CountDownLatch(1);
        CountDownLatch distributionCanCommit = new CountDownLatch(1);
        CompletableFuture<Void> distribution = CompletableFuture.runAsync(
                () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    Instant now = Instant.now();
                    Notice notice = noticeRepository.findDistributableForUpdate(noticeId, now)
                            .orElseThrow();
                    distributionLocked.countDown();
                    await(distributionCanCommit);
                    notificationRepository.createNoticeNotifications(notice, now);
                    notice.distribute(now);
                }), executor);
        await(distributionLocked);

        // When
        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> noticeService.deleteNotice(noticeId, ADMIN_ID, "잘못 보낸 공지"), executor);
        try {
            awaitLockWaiting();
            assertThat(deletion.isDone()).isFalse();
        } finally {
            distributionCanCommit.countDown();
        }
        distribution.get(5, TimeUnit.SECONDS);
        deletion.get(5, TimeUnit.SECONDS);

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT distributed_at IS NOT NULL AND deleted_at IS NOT NULL FROM notices",
                Boolean.class)).isTrue();
    }

    // 삭제 서비스 대신 삭제와 같은 잠금 조회 후 삭제 표시를 해, 삭제가 잡는 공지 행 잠금을 재현한다.
    @Test
    @DisplayName("삭제가 공지를 잠그고 있으면 배포는 그 공지를 건너뛰고 삭제 후에도 배포하지 않는다")
    void distributeNotices_deletionLocksFirst_neverDistributesDeletedNotice() throws Exception {
        // Given
        CountDownLatch deletionLocked = new CountDownLatch(1);
        CountDownLatch deletionCanCommit = new CountDownLatch(1);
        CompletableFuture<Void> deletion = CompletableFuture.runAsync(
                () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    noticeRepository.findByIdForUpdate(noticeId).orElseThrow()
                            .delete(Instant.now());
                    deletionLocked.countDown();
                    await(deletionCanCommit);
                }), executor);
        await(deletionLocked);

        // When
        try {
            distributionService.distributeNotices();
        } finally {
            deletionCanCommit.countDown();
        }
        deletion.get(5, TimeUnit.SECONDS);
        distributionService.distributeNotices();

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT distributed_at IS NULL AND deleted_at IS NOT NULL FROM notices",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications",
                Integer.class)).isZero();
    }

    // 수정·삭제 요청이 공지 행 잠금을 기다리기 시작할 때까지 확인한다.
    private void awaitLockWaiting() throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
        while (Instant.now().isBefore(deadline)) {
            Integer waiting = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM pg_stat_activity
                    WHERE datname = current_database() AND wait_event_type = 'Lock'
                        AND query ILIKE '%notices%'
                    """, Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("공지 수정·삭제가 공지 행 잠금을 기다리지 않았습니다.");
    }

    // 공용 정리 대상이 아닌 관리자·감사 로그는 이 테스트가 만든 행만 지운다.
    private void clean() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("다른 트랜잭션의 잠금 신호를 받지 못했습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
