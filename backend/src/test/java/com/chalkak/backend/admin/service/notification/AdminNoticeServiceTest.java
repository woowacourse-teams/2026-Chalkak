package com.chalkak.backend.admin.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.domain.AdminAction;
import com.chalkak.backend.admin.domain.AdminAuditLog;
import com.chalkak.backend.admin.domain.AdminTargetType;
import com.chalkak.backend.common.logging.LogFields;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.notification.domain.Notice;
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
    @DisplayName("공지를 등록하면 정규화한 내용을 저장하고 감사 로그에는 본문 대신 해시를 남긴다")
    void createNotice_validInput_persistsNoticeAndAuditLog() throws Exception {
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
                "scheduledAt", scheduledAt.toString(),
                "bodySha256", sha256("10월 20일 02:00부터 점검합니다.")));
        assertThat(auditLog.getReason()).isNull();
        assertThat(auditLog.getRequestId()).isEqualTo(requestId);
    }

    @Test
    @DisplayName("본문에 설정값 형식 문구나 이미지 링크가 있어도 공지를 등록한다")
    void createNotice_sensitiveLookingBody_persistsNotice() {
        // Given
        String body = "비밀번호 정책 변경 안내 — password: 8자 이상\nhttps://cdn.example.com/banner.png";

        // When
        AdminNoticeResult result = noticeService.createNotice(ADMIN_ID, "비밀번호 정책 변경 안내",
                body, Instant.now());
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(entityManager.find(Notice.class, result.noticeId()).getBody()).isEqualTo(body);
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

    @Test
    @DisplayName("공지 목록은 등록 최신순으로 요청한 페이지만 조회하고 다음 페이지 여부를 알려준다")
    void getNotices_multipleNotices_returnsLatestCreatedFirst() {
        // Given
        UUID oldest = insertNotice("첫 공지", "2026-10-10T00:00:00Z");
        UUID middle = insertNotice("둘째 공지", "2026-10-11T00:00:00Z");
        UUID latest = insertNotice("셋째 공지", "2026-10-12T00:00:00Z");

        // When
        AdminNoticeListResult firstPage = noticeService.getNotices(1, 2);
        AdminNoticeListResult secondPage = noticeService.getNotices(2, 2);

        // Then
        assertThat(firstPage.notices()).extracting(AdminNoticeResult::noticeId)
                .containsExactly(latest, middle);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.currentPage()).isEqualTo(1);
        assertThat(firstPage.pageSize()).isEqualTo(2);
        assertThat(secondPage.notices()).extracting(AdminNoticeResult::noticeId)
                .containsExactly(oldest);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    @DisplayName("등록된 공지가 없으면 빈 목록을 조회한다")
    void getNotices_noNotices_returnsEmptyList() {
        // When
        AdminNoticeListResult result = noticeService.getNotices(1, 20);

        // Then
        assertThat(result.notices()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0, 20", "1, 0", "1, 101"})
    @DisplayName("페이지 번호나 페이지 크기가 범위를 벗어나면 거절한다")
    void getNotices_invalidPagination_throwsBusinessException(int page, int pageSize) {
        // When & Then
        assertThatThrownBy(() -> noticeService.getNotices(page, pageSize))
                .isInstanceOf(BusinessException.class)
                .hasMessage("조회 조건이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("공지 상세는 저장된 제목·본문·예약 시각을 조회한다")
    void getNotice_existingNotice_returnsNotice() {
        // Given
        UUID noticeId = insertNotice("서비스 점검 안내", "2026-10-12T00:00:00Z");

        // When
        AdminNoticeResult result = noticeService.getNotice(noticeId);

        // Then
        assertThat(result.noticeId()).isEqualTo(noticeId);
        assertThat(result.title()).isEqualTo("서비스 점검 안내");
        assertThat(result.body()).isEqualTo("점검합니다.");
        assertThat(result.scheduledAt()).isEqualTo(Instant.parse("2026-10-19T09:00:00Z"));
    }

    @Test
    @DisplayName("없는 공지를 상세 조회하면 찾을 수 없음 예외를 던진다")
    void getNotice_unknownNotice_throwsNotFoundException() {
        // When & Then
        assertThatThrownBy(() -> noticeService.getNotice(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("공지를 찾을 수 없습니다.");
    }

    private UUID insertNotice(String title, String createdAt) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO notices (title, body, scheduled_at, created_at, updated_at)
                VALUES (?, '점검합니다.', '2026-10-19T09:00:00Z', CAST(? AS TIMESTAMPTZ),
                    CAST(? AS TIMESTAMPTZ))
                RETURNING id
                """, UUID.class, title, createdAt, createdAt);
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
