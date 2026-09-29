package com.chalkak.backend.admin.service.topic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.topic.domain.TopicPhase;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminTopicCommandServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("0198fd20-0000-7000-8000-000000000001");
    private static final UUID BEFORE_TOPIC_ID = UUID
            .fromString("0198fd20-0000-7000-8000-000000000011");
    private static final UUID OPEN_TOPIC_ID = UUID
            .fromString("0198fd20-0000-7000-8000-000000000012");

    @Autowired
    private AdminTopicCommandService adminTopicCommandService;

    @Autowired
    private AdminTopicQueryService adminTopicQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(NOW);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'topic-manager', 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
        insertTopic(
                BEFORE_TOPIC_ID,
                "공개 전 주제",
                LocalDate.of(2026, 8, 30),
                Instant.parse("2026-08-29T15:00:00Z"),
                Instant.parse("2026-08-30T15:00:00Z"));
        insertTopic(
                OPEN_TOPIC_ID,
                "참여 중 주제",
                LocalDate.of(2026, 8, 28),
                Instant.parse("2026-08-27T15:00:00Z"),
                Instant.parse("2026-08-28T15:00:00Z"));
    }

    @Test
    @DisplayName("주제를 생성하면 관리자 감사 로그를 남긴다")
    void createTopic_validRequest_savesTopicAndAuditLog() {
        AdminTopicDetail result = adminTopicCommandService.createTopic(
                ADMIN_ID,
                "  새 주제  ",
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z"));
        flushAndClear();

        assertThat(result.title()).isEqualTo("새 주제");
        assertThat(result.phase()).isEqualTo(TopicPhase.BEFORE_OPEN);
        assertThat(countAudit(result.topicId(), "TOPIC_CREATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 주제와 날짜가 중복되면 생성하지 않는다")
    void createTopic_duplicateDate_throwsBusinessException() {
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicCommandService.createTopic(
                        ADMIN_ID,
                        "중복 주제",
                        LocalDate.of(2026, 8, 30),
                        Instant.parse("2026-08-29T15:00:00Z"),
                        Instant.parse("2026-08-30T15:00:00Z")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("날짜가 달라도 참여 기간이 겹치면 주제를 생성하지 않는다")
    void createTopic_overlappingPeriod_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicCommandService.createTopic(
                        ADMIN_ID,
                        "겹치는 주제",
                        LocalDate.of(2026, 8, 29),
                        Instant.parse("2026-08-29T14:00:00Z"),
                        Instant.parse("2026-08-29T16:00:00Z")));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("다른 주제의 참여 기간과 겹칩니다.");
    }

    @Test
    @DisplayName("이전 종료와 다음 시작이 같은 시각이면 주제를 생성한다")
    void createTopic_adjacentPeriod_savesTopic() {
        // When
        AdminTopicDetail result = adminTopicCommandService.createTopic(
                ADMIN_ID,
                "사이 주제",
                LocalDate.of(2026, 8, 29),
                Instant.parse("2026-08-28T15:00:00Z"),
                Instant.parse("2026-08-29T15:00:00Z"));

        // Then
        assertThat(result.title()).isEqualTo("사이 주제");
    }

    @Test
    @DisplayName("삭제된 주제의 기간은 새 주제를 막지 않는다")
    void createTopic_overlappingDeletedTopic_savesTopic() {
        // Given
        jdbcTemplate.update(
                "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                BEFORE_TOPIC_ID);

        // When
        AdminTopicDetail result = adminTopicCommandService.createTopic(
                ADMIN_ID,
                "새 주제",
                LocalDate.of(2026, 8, 29),
                Instant.parse("2026-08-29T14:00:00Z"),
                Instant.parse("2026-08-29T16:00:00Z"));

        // Then
        assertThat(result.title()).isEqualTo("새 주제");
    }

    @Test
    @DisplayName("DB는 날짜가 다른 주제라도 참여 기간이 겹치면 저장하지 않는다")
    void insertTopic_overlappingPeriod_violatesDatabaseConstraint() {
        assertThatThrownBy(() -> insertTopic(
                UUID.fromString("0198fd20-0000-7000-8000-000000000014"),
                "겹치는 주제",
                LocalDate.of(2026, 8, 29),
                Instant.parse("2026-08-29T14:00:00Z"),
                Instant.parse("2026-08-29T16:00:00Z")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("공개 전 주제를 수정하면 변경 전후 감사 로그를 남긴다")
    void updateTopic_beforeOpen_updatesAndAudits() {
        AdminTopicDetail result = adminTopicCommandService.updateTopic(
                BEFORE_TOPIC_ID,
                ADMIN_ID,
                "수정한 주제",
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z"));
        flushAndClear();

        assertThat(result.title()).isEqualTo("수정한 주제");
        assertThat(countAudit(BEFORE_TOPIC_ID, "TOPIC_UPDATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("공개 전 주제의 종료 시각을 늘려 다른 주제와 겹치면 수정하지 않는다")
    void updateTopic_overlappingPeriod_throwsBusinessException() {
        // Given
        insertTopic(
                UUID.fromString("0198fd20-0000-7000-8000-000000000013"),
                "다음 주제",
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z"));

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicCommandService.updateTopic(
                        BEFORE_TOPIC_ID,
                        ADMIN_ID,
                        "공개 전 주제",
                        LocalDate.of(2026, 8, 30),
                        Instant.parse("2026-08-29T15:00:00Z"),
                        Instant.parse("2026-08-30T16:00:00Z")));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception.getMessage()).isEqualTo("다른 주제의 참여 기간과 겹칩니다.");
        assertThat(countAudit(BEFORE_TOPIC_ID, "TOPIC_UPDATED")).isZero();
    }

    @Test
    @DisplayName("참여 중인 주제는 수정할 수 없다")
    void updateTopic_openTopic_throwsStateChanged() {
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicCommandService.updateTopic(
                        OPEN_TOPIC_ID,
                        ADMIN_ID,
                        "수정 시도",
                        LocalDate.of(2026, 8, 29),
                        Instant.parse("2026-08-28T15:00:00Z"),
                        Instant.parse("2026-08-29T15:00:00Z")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
    }

    @Test
    @DisplayName("참여 중인 주제는 변경 기간이 다른 주제와 겹쳐도 상태 오류를 먼저 반환한다")
    void updateTopic_openTopicWithOverlappingPeriod_throwsStateChanged() {
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicCommandService.updateTopic(
                        OPEN_TOPIC_ID,
                        ADMIN_ID,
                        "수정 시도",
                        LocalDate.of(2026, 8, 28),
                        Instant.parse("2026-08-27T15:00:00Z"),
                        Instant.parse("2026-08-30T15:00:00Z")));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
    }

    @Test
    @DisplayName("공개 전 주제를 사유와 함께 삭제하면 목록에서 제외하고 감사 로그를 남긴다")
    void deleteTopic_beforeOpen_softDeletesAndAudits() {
        adminTopicCommandService.deleteTopic(BEFORE_TOPIC_ID, ADMIN_ID, "주제 편성 변경");
        flushAndClear();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM topics WHERE id = ?",
                Boolean.class,
                BEFORE_TOPIC_ID)).isTrue();
        assertThat(countAudit(BEFORE_TOPIC_ID, "TOPIC_DELETED")).isEqualTo(1);
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminTopicQueryService.getTopic(BEFORE_TOPIC_ID));
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    private int countAudit(UUID topicId, String action) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM admin_audit_logs
                WHERE target_id = ? AND CAST(action AS TEXT) = ?
                """, Integer.class, topicId, action);
    }

    private void insertTopic(
            UUID id,
            String title,
            LocalDate topicDate,
            Instant startsAt,
            Instant endsAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at, deleted_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                """,
                id,
                title,
                topicDate,
                Timestamp.from(startsAt),
                Timestamp.from(endsAt));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
