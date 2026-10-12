package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.notification.domain.Notice;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminNoticeServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID = UUID.fromString("019a0000-0000-7000-8000-000000000592");

    @Autowired
    private AdminNoticeService noticeService;

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
                        VALUES (?, 'notice-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        MDC.remove(LogFields.REQUEST_ID);
    }

    @Test
    @DisplayName("공지를 등록하면 정규화한 내용과 예약 시각을 저장하고 작성 감사 로그를 남긴다")
    void createNotice_validInput_persistsNoticeAndAuditLog() {
        // Given
        Instant scheduledAt = Instant.now().plus(Duration.ofHours(1))
                .truncatedTo(ChronoUnit.MICROS);

        // When
        AdminNoticeResult result = noticeService.createNotice(ADMIN_ID, " 서비스 점검 안내 ",
                "10월 20일 02:00부터 점검합니다.", scheduledAt);
        entityManager.flush();
        entityManager.clear();

        // Then
        Notice stored = entityManager.find(Notice.class, result.noticeId());
        assertThat(stored.getTitle()).isEqualTo("서비스 점검 안내");
        assertThat(stored.getBody()).isEqualTo("10월 20일 02:00부터 점검합니다.");
        assertThat(stored.getScheduledAt()).isEqualTo(scheduledAt);
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        AdminAuditLog auditLog = entityManager.createQuery("""
                SELECT auditLog FROM AdminAuditLog auditLog
                WHERE auditLog.targetId = :targetId AND auditLog.action = :action
                """, AdminAuditLog.class)
                .setParameter("targetId", result.noticeId())
                .setParameter("action", AdminAction.NOTICE_CREATED)
                .getSingleResult();
        assertThat(auditLog.getActorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(auditLog.getTargetType()).isEqualTo(AdminTargetType.NOTICE);
        assertThat(auditLog.getBeforeState()).isEmpty();
        assertThat(auditLog.getAfterState()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "title", "서비스 점검 안내",
                "body", "10월 20일 02:00부터 점검합니다.",
                "scheduledAt", scheduledAt.toString()));
        assertThat(auditLog.getReason()).isNull();
        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("현재 시각 기준 5분 넘게 과거인 예약 시각은 거절하고 저장하지 않는다")
    void createNotice_pastScheduledAt_throwsWithoutPersisting() {
        // Given
        Instant scheduledAt = Instant.now().minus(Duration.ofHours(1));

        // When & Then
        assertThatThrownBy(() -> noticeService.createNotice(ADMIN_ID, "서비스 점검 안내",
                "점검합니다.", scheduledAt))
                .isInstanceOf(BusinessException.class)
                .hasMessage("공지 예약 시각은 현재 시각보다 5분 넘게 과거일 수 없습니다.");
        assertThat(countRows("notices")).isZero();
        assertThat(countRows("admin_audit_logs")).isZero();
    }

    @Test
    @DisplayName("관리자 식별자가 없으면 거절하고 저장하지 않는다")
    void createNotice_nullAdminId_throwsWithoutPersisting() {
        // When & Then
        assertThatThrownBy(() -> noticeService.createNotice(null, "서비스 점검 안내",
                "점검합니다.", Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("관리자 식별자가 필요합니다.");
        assertThat(countRows("notices")).isZero();
    }

    private int countRows(String table) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
