package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminTopicServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-28T03:00:00Z");
    private static final UUID ADMIN_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000001");
    private static final UUID BEFORE_TOPIC_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000011");
    private static final UUID OPEN_TOPIC_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000012");
    private static final UUID CLOSED_TOPIC_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000013");
    private static final UUID DELETED_TOPIC_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000014");
    private static final UUID USER_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000021");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000022");
    private static final UUID POST_ID =
            UUID.fromString("0198fd20-0000-7000-8000-000000000023");

    @Autowired
    private AdminTopicService adminTopicService;

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
                Instant.parse("2026-08-30T15:00:00Z"),
                null
        );
        insertTopic(
                OPEN_TOPIC_ID,
                "참여 중 주제",
                LocalDate.of(2026, 8, 28),
                Instant.parse("2026-08-27T15:00:00Z"),
                Instant.parse("2026-08-28T15:00:00Z"),
                null
        );
        insertTopic(
                CLOSED_TOPIC_ID,
                "종료된 주제",
                LocalDate.of(2026, 8, 26),
                Instant.parse("2026-08-25T15:00:00Z"),
                Instant.parse("2026-08-26T15:00:00Z"),
                null
        );
        insertTopic(
                DELETED_TOPIC_ID,
                "삭제된 주제",
                LocalDate.of(2026, 9, 1),
                Instant.parse("2026-08-31T15:00:00Z"),
                Instant.parse("2026-09-01T15:00:00Z"),
                NOW
        );
        insertOpenTopicPost();
    }

    @Test
    @DisplayName("관리자 목록은 삭제 주제를 제외하고 Clock 기준 phase와 날짜 필터를 적용한다")
    void getTopics_phaseAndDateFilters_returnsMatchingActiveTopics() {
        AdminTopicListResult result = adminTopicService.getTopics(
                TopicPhase.BEFORE_OPEN,
                LocalDate.of(2026, 8, 29),
                LocalDate.of(2026, 8, 31),
                AdminTopicSort.TOPIC_DATE_DESC,
                1,
                20
        );

        assertThat(result.topics()).singleElement().satisfies(topic -> {
            assertThat(topic.topicId()).isEqualTo(BEFORE_TOPIC_ID);
            assertThat(topic.phase()).isEqualTo(TopicPhase.BEFORE_OPEN);
        });
        assertThat(result.hasNext()).isFalse();
    }

    @Test
    @DisplayName("phase 경계에서 시작 시각은 OPEN이고 종료 시각은 CLOSED다")
    void getTopic_phaseBoundaries_derivesFromClock() {
        given(clock.instant()).willReturn(Instant.parse("2026-08-27T15:00:00Z"));
        assertThat(adminTopicService.getTopic(OPEN_TOPIC_ID).phase())
                .isEqualTo(TopicPhase.OPEN);

        given(clock.instant()).willReturn(Instant.parse("2026-08-28T15:00:00Z"));
        assertThat(adminTopicService.getTopic(OPEN_TOPIC_ID).phase())
                .isEqualTo(TopicPhase.CLOSED);
    }

    @Test
    @DisplayName("주제 상세은 게시물 검수 상태별 통계를 함께 반환한다")
    void getTopic_existingPosts_returnsModerationCounts() {
        AdminTopicDetail result = adminTopicService.getTopic(OPEN_TOPIC_ID);

        assertThat(result.postCounts()).isEqualTo(
                new AdminTopicDetail.PostCounts(1, 0, 0)
        );
    }

    @Test
    @DisplayName("주제를 생성하면 관리자 감사 로그를 남긴다")
    void createTopic_validRequest_savesTopicAndAuditLog() {
        AdminTopicDetail result = adminTopicService.createTopic(
                ADMIN_ID,
                "  새 주제  ",
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z")
        );
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
                () -> adminTopicService.createTopic(
                        ADMIN_ID,
                        "중복 주제",
                        LocalDate.of(2026, 8, 30),
                        Instant.parse("2026-08-29T15:00:00Z"),
                        Instant.parse("2026-08-30T15:00:00Z")
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("공개 전 주제를 수정하면 변경 전후 감사 로그를 남긴다")
    void updateTopic_beforeOpen_updatesAndAudits() {
        AdminTopicDetail result = adminTopicService.updateTopic(
                BEFORE_TOPIC_ID,
                ADMIN_ID,
                "수정한 주제",
                LocalDate.of(2026, 8, 31),
                Instant.parse("2026-08-30T15:00:00Z"),
                Instant.parse("2026-08-31T15:00:00Z")
        );
        flushAndClear();

        assertThat(result.title()).isEqualTo("수정한 주제");
        assertThat(countAudit(BEFORE_TOPIC_ID, "TOPIC_UPDATED")).isEqualTo(1);
    }

    @Test
    @DisplayName("참여 중인 주제는 수정할 수 없다")
    void updateTopic_openTopic_throwsStateChanged() {
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminTopicService.updateTopic(
                        OPEN_TOPIC_ID,
                        ADMIN_ID,
                        "수정 시도",
                        LocalDate.of(2026, 8, 29),
                        Instant.parse("2026-08-28T15:00:00Z"),
                        Instant.parse("2026-08-29T15:00:00Z")
                )
        );

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_CHANGED);
    }

    @Test
    @DisplayName("공개 전 주제를 사유와 함께 삭제하면 목록에서 제외하고 감사 로그를 남긴다")
    void deleteTopic_beforeOpen_softDeletesAndAudits() {
        adminTopicService.deleteTopic(BEFORE_TOPIC_ID, ADMIN_ID, "주제 편성 변경");
        flushAndClear();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM topics WHERE id = ?",
                Boolean.class,
                BEFORE_TOPIC_ID
        )).isTrue();
        assertThat(countAudit(BEFORE_TOPIC_ID, "TOPIC_DELETED")).isEqualTo(1);
        assertThatThrownByNotFound(BEFORE_TOPIC_ID);
    }

    @Test
    @DisplayName("삭제된 주제 상세은 찾을 수 없음으로 응답한다")
    void getTopic_deletedTopic_throwsNotFoundException() {
        assertThatThrownByNotFound(DELETED_TOPIC_ID);
    }

    private void assertThatThrownByNotFound(UUID topicId) {
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminTopicService.getTopic(topicId)
        );
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
            Instant endsAt,
            Instant deletedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at, deleted_at
                ) VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
                """,
                id,
                title,
                topicDate,
                Timestamp.from(startsAt),
                Timestamp.from(endsAt),
                deletedAt == null ? null : Timestamp.from(deletedAt));
    }

    private void insertOpenTopicPost() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'topic-count-author@example.com', 'ACTIVE',
                    'signatures/topic-count-author', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (id, original_storage_key, created_at, updated_at)
                VALUES (?, 'posts/topic-count-original', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, PHOTO_ID);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, POST_ID, USER_ID, OPEN_TOPIC_ID, PHOTO_ID);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
