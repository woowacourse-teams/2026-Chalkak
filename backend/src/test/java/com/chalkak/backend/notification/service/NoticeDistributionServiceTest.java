package com.chalkak.backend.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class NoticeDistributionServiceTest extends IntegrationTestSupport {

    @Autowired
    private NoticeDistributionService distributionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID activeUser;
    private UUID bannedUser;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        activeUser = insertUser("ACTIVE", false);
        bannedUser = insertUser("BANNED", false);
        insertUser("ACTIVE", true);
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
    }

    @Test
    @DisplayName("예약 시각이 지난 공지를 탈퇴하지 않은 회원 전체의 알림함에 발행 대기로 배포한다")
    void distributeNotices_dueNotice_createsNotificationsForNonWithdrawnUsers() {
        // Given
        UUID noticeId = insertNotice(Instant.now().minus(Duration.ofMinutes(1)));

        // When
        distributionService.distributeNotices();

        // Then
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT user_id, source_type, source_id, event_key, type, title, body,
                    sqs_publish_status, created_at, next_attempt_at
                FROM notifications
                """);
        assertThat(rows).extracting(row -> row.get("user_id"))
                .containsExactlyInAnyOrder(activeUser, bannedUser);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.get("source_type")).isEqualTo("NOTICE");
            assertThat(row.get("type")).isEqualTo("NOTICE");
            assertThat(row.get("source_id")).isEqualTo(noticeId);
            assertThat(row.get("event_key")).isEqualTo(noticeId);
            assertThat(row.get("title")).isEqualTo("서비스 점검 안내");
            assertThat(row.get("body")).isEqualTo("점검합니다.");
            assertThat(row.get("sqs_publish_status")).isEqualTo("PENDING");
            assertThat(row.get("next_attempt_at")).isEqualTo(row.get("created_at"));
        });
        Map<String, Object> notice = jdbcTemplate.queryForMap(
                "SELECT status, distributed_at FROM notices WHERE id = ?", noticeId);
        assertThat(notice.get("status")).isEqualTo("DISTRIBUTED");
        assertThat(notice.get("distributed_at")).isEqualTo(rows.getFirst().get("created_at"));
    }

    @Test
    @DisplayName("예약 시각 전인 공지는 배포하지 않는다")
    void distributeNotices_futureNotice_keepsScheduled() {
        // Given
        UUID noticeId = insertNotice(Instant.now().plus(Duration.ofHours(1)));

        // When
        distributionService.distributeNotices();

        // Then
        assertThat(countNotifications()).isZero();
        assertThat(statusOf(noticeId)).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("배포를 다시 실행해도 이미 배포한 공지의 알림을 추가로 만들지 않는다")
    void distributeNotices_repeated_createsOnlyOnce() {
        // Given
        insertNotice(Instant.now().minus(Duration.ofMinutes(1)));
        distributionService.distributeNotices();

        // When
        distributionService.distributeNotices();

        // Then
        assertThat(countNotifications()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 공지의 배포가 실패하면 그 공지는 예약 상태로 남고 다른 공지는 배포한다")
    void distributeNotices_oneNoticeFails_distributesOthers() {
        // Given
        UUID failing = insertNotice(Instant.now().minus(Duration.ofMinutes(2)));
        UUID other = insertNotice(Instant.now().minus(Duration.ofMinutes(1)));
        insertConflictingNotification(activeUser, failing);

        // When
        distributionService.distributeNotices();

        // Then
        assertThat(statusOf(failing)).isEqualTo("SCHEDULED");
        assertThat(statusOf(other)).isEqualTo("DISTRIBUTED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE source_id = ?", Integer.class, other))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("두 실행이 동시에 같은 공지를 배포해도 회원당 알림은 하나만 생긴다")
    void distributeNotices_concurrentRuns_createsOncePerUser() throws Exception {
        // Given
        UUID noticeId = insertNotice(Instant.now().minus(Duration.ofMinutes(1)));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // When
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> runAfter(start)),
                    executor.submit(() -> runAfter(start)));
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdown();
        }

        // Then
        assertThat(countNotifications()).isEqualTo(2);
        assertThat(statusOf(noticeId)).isEqualTo("DISTRIBUTED");
    }

    private void runAfter(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return;
        }
        distributionService.distributeNotices();
    }

    private UUID insertUser(String status, boolean withdrawn) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, CAST(? AS user_status), ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CASE WHEN ? THEN CURRENT_TIMESTAMP END)
                """, userId, userId + "@notice.test", status, userId + "/signature.png",
                withdrawn);
        return userId;
    }

    private UUID insertNotice(Instant scheduledAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notices (title, body, scheduled_at, status, created_at, updated_at)
                VALUES ('서비스 점검 안내', '점검합니다.', ?, 'SCHEDULED', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP)
                RETURNING id
                """, UUID.class, Timestamp.from(scheduledAt));
    }

    private void insertConflictingNotification(UUID userId, UUID noticeId) {
        jdbcTemplate.update("""
                INSERT INTO notifications (user_id, source_type, source_id, event_key, type,
                    title, body, created_at, sqs_publish_status)
                VALUES (?, 'NOTICE', ?, ?, 'NOTICE', '공지', '본문', CURRENT_TIMESTAMP,
                    'NOT_REQUIRED')
                """, userId, noticeId, noticeId);
    }

    private int countNotifications() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class);
    }

    private String statusOf(UUID noticeId) {
        return jdbcTemplate.queryForObject("SELECT status FROM notices WHERE id = ?",
                String.class, noticeId);
    }
}
