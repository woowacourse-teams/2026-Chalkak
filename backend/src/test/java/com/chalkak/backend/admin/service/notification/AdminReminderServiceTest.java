package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.Reminder;
import com.chalkak.backend.notification.domain.ReminderRepeat;
import com.chalkak.backend.notification.domain.ReminderTarget;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminReminderServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000724");
    private static final String TITLE = "오늘이 얼마 남지 않았어요";
    private static final String BODY = "사진을 자랑해 주세요!";

    @Autowired
    private AdminReminderService reminderService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID requestId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MDC.put(LogFields.REQUEST_ID, requestId.toString());
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'reminder-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("리마인더를 등록하면 정규화한 내용을 저장하고 감사 로그에는 본문 대신 해시를 남긴다")
    void createReminder_validInput_persistsReminderAndAuditLog() throws Exception {
        // Given
        Instant scheduledAt = Instant.now().plus(Duration.ofHours(1))
                .truncatedTo(ChronoUnit.MICROS);

        // When
        AdminReminderResult result = reminderService.createReminder(
                ADMIN_ID,
                " " + TITLE + " ",
                BODY,
                ReminderRepeat.DAILY,
                ReminderTarget.TOPIC_NON_PARTICIPANTS,
                scheduledAt
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        Reminder stored = entityManager.find(Reminder.class, result.reminderId());
        assertThat(stored.getTitle()).isEqualTo(TITLE);
        assertThat(stored.getBody()).isEqualTo(BODY);
        assertThat(stored.getRepeat()).isEqualTo(ReminderRepeat.DAILY);
        assertThat(stored.getTarget()).isEqualTo(ReminderTarget.TOPIC_NON_PARTICIPANTS);
        assertThat(stored.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        AdminAuditLog auditLog = entityManager.createQuery("""
                SELECT auditLog FROM AdminAuditLog auditLog
                WHERE auditLog.targetId = :targetId AND auditLog.action = :action
                """, AdminAuditLog.class)
                .setParameter("targetId", result.reminderId())
                .setParameter("action", AdminAction.REMINDER_CREATED)
                .getSingleResult();
        assertThat(auditLog.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(auditLog.getTargetType()).isEqualTo(AdminTargetType.REMINDER);
        assertThat(auditLog.getBeforeState()).isEmpty();
        assertThat(auditLog.getAfterState()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "title",
                TITLE,
                "bodySha256",
                sha256(BODY),
                "repeat",
                "DAILY",
                "target",
                "TOPIC_NON_PARTICIPANTS",
                "scheduledAt",
                scheduledAt.toString()
        ));
        assertThat(auditLog.getReason()).isNull();
        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("현재 시각 기준 5분 넘게 과거인 발송 시각은 거절하고 저장하지 않는다")
    void createReminder_pastScheduledAt_throwsWithoutPersisting() {
        // When & Then
        assertThatThrownBy(() -> reminderService.createReminder(
                ADMIN_ID,
                TITLE,
                BODY,
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                Instant.now().minus(Duration.ofHours(1))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("리마인더 발송 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다.");
        assertThat(countRows("reminders")).isZero();
        assertThat(countRows("admin_audit_logs")).isZero();
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 거절하고 저장하지 않는다")
    void createReminder_nullAdminId_throwsWithoutPersisting() {
        // When & Then
        assertThatThrownBy(() -> reminderService.createReminder(
                null,
                TITLE,
                BODY,
                ReminderRepeat.ONCE,
                ReminderTarget.ALL,
                Instant.now()
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 식별자가 필요합니다.");
        assertThat(countRows("reminders")).isZero();
    }

    @Test
    @DisplayName("리마인더 목록은 등록 최신순으로 요청한 페이지만 조회하고 다음 페이지 여부를 알려준다")
    void getReminders_multipleReminders_returnsLatestCreatedFirst() {
        // Given
        UUID oldest = insertReminder("2026-10-19T00:00:00Z");
        UUID middle = insertReminder("2026-10-20T00:00:00Z");
        UUID latest = insertReminder("2026-10-21T00:00:00Z");

        // When
        AdminReminderListResult firstPage = reminderService.getReminders(1, 2);
        AdminReminderListResult secondPage = reminderService.getReminders(2, 2);

        // Then
        assertThat(firstPage.reminders()).extracting(AdminReminderResult::reminderId)
                .containsExactly(latest, middle);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.currentPage()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(secondPage.reminders()).extracting(AdminReminderResult::reminderId)
                .containsExactly(oldest);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("등록된 리마인더가 없으면 빈 목록을 조회한다")
    void getReminders_noReminders_returnsEmptyList() {
        // When
        AdminReminderListResult result = reminderService.getReminders(1, 20);

        // Then
        assertThat(result.reminders()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101"})
    @DisplayName("페이지 번호나 페이지 크기가 범위를 벗어나면 거절한다")
    void getReminders_invalidPagination_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> reminderService.getReminders(page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
    }

    private UUID insertReminder(String createdAt) {
        return jdbcTemplate.queryForObject(
                """
                        INSERT INTO reminders (title, body, repeat, target, scheduled_at, created_at, updated_at)
                        VALUES ('오늘이 얼마 남지 않았어요', '사진을 자랑해 주세요!', 'DAILY', 'ALL',
                            '2026-10-25T12:00:00Z', CAST(? AS TIMESTAMPTZ), CAST(? AS TIMESTAMPTZ))
                        RETURNING id
                        """,
                UUID.class,
                createdAt,
                createdAt
        );
    }

    private String sha256(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private int countRows(String table) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
