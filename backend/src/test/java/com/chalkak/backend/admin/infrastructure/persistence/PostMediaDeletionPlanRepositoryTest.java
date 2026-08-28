package com.chalkak.backend.admin.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.chalkak.backend.admin.repository.PostMediaDeletionPlanRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostMediaDeletionPlanRepositoryImpl.class)
class PostMediaDeletionPlanRepositoryTest {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657601");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657602");

    private static final UUID EARLY_DUE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657611");
    private static final UUID LATE_DUE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657612");
    private static final UUID FUTURE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657613");
    private static final UUID SUCCEEDED_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657614");
    private static final UUID LATE_FAILURE_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b657615");

    private static final Instant DUE_AT = Instant.parse("2026-08-28T04:00:00Z");

    @Autowired
    private PostMediaDeletionPlanRepository deletionPlanRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insertUser();
        insertTopic();
        insertPost(EARLY_DUE_POST_ID, 1);
        insertPost(LATE_DUE_POST_ID, 2);
        insertPost(FUTURE_POST_ID, 3);
        insertPost(SUCCEEDED_POST_ID, 4);
        insertPost(LATE_FAILURE_POST_ID, 5);
    }

    @Test
    @DisplayName("재시도 시각이 도래한 미완료 계획만 오래된 순서와 요청한 개수로 조회한다")
    void findDuePostIds_dueIncompletePlans_returnsOrderedLimitedPostIds() {
        // Given
        insertPlan(
                EARLY_DUE_POST_ID,
                "PENDING",
                0,
                null,
                DUE_AT.minusSeconds(120),
                null
        );
        insertPlan(
                LATE_DUE_POST_ID,
                "FAILED",
                2,
                "STORAGE_DELETE_FAILED",
                DUE_AT.minusSeconds(30),
                null
        );
        insertPlan(
                FUTURE_POST_ID,
                "PENDING",
                0,
                null,
                DUE_AT.plusSeconds(1),
                null
        );
        insertPlan(
                SUCCEEDED_POST_ID,
                "SUCCEEDED",
                1,
                null,
                null,
                DUE_AT.minusSeconds(60)
        );

        // When
        List<UUID> duePostIds = deletionPlanRepository.findDuePostIds(DUE_AT, 10);
        List<UUID> limitedPostIds = deletionPlanRepository.findDuePostIds(DUE_AT, 1);

        // Then
        assertThat(duePostIds).containsExactly(EARLY_DUE_POST_ID, LATE_DUE_POST_ID);
        assertThat(limitedPostIds).containsExactly(EARLY_DUE_POST_ID);
    }

    @Test
    @DisplayName("성공 처리 뒤 늦게 도착한 실패 갱신은 완료 상태를 덮어쓰지 않는다")
    void markFailedIfIncomplete_afterSucceeded_doesNotOverwriteSucceededPlan() {
        // Given
        insertPlan(
                LATE_FAILURE_POST_ID,
                "PENDING",
                0,
                null,
                DUE_AT.minusSeconds(60),
                null
        );
        Instant completedAt = DUE_AT.plusSeconds(10);
        Instant lateFailureAt = completedAt.plusSeconds(5);
        Instant nextAttemptAt = lateFailureAt.plusSeconds(60);

        // When
        int succeededRows = deletionPlanRepository.markSucceededIfIncomplete(
                LATE_FAILURE_POST_ID,
                completedAt
        );
        int failedRows = deletionPlanRepository.markFailedIfIncomplete(
                LATE_FAILURE_POST_ID,
                "STORAGE_DELETE_FAILED",
                nextAttemptAt,
                lateFailureAt
        );

        // Then
        DeletionPlanState state = findPlanState(LATE_FAILURE_POST_ID);
        assertThat(succeededRows).isEqualTo(1);
        assertThat(failedRows).isZero();
        assertThat(state.status()).isEqualTo("SUCCEEDED");
        assertThat(state.attemptCount()).isEqualTo(1);
        assertThat(state.lastErrorCode()).isNull();
        assertThat(state.nextAttemptAt()).isNull();
        assertThat(state.completedAt()).isEqualTo(completedAt);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'deletion-repository@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/deletion-repository/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '미디어 삭제 저장소', '2099-12-21',
                    '2099-12-21T00:00:00Z', '2099-12-22T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
    }

    private void insertPost(UUID postId, int sequence) {
        UUID photoId = UUID.fromString(String.format(
                "0198f6c1-62ba-7d30-8b12-0f733b6577%02d",
                sequence
        ));
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (?, ?, CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, photoId, "chalkak/posts/dev/original/" + postId + ".webp");
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, moderation_status,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, CAST('REJECTED' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, postId, USER_ID, TOPIC_ID, photoId);
    }

    private void insertPlan(
            UUID postId,
            String status,
            int attemptCount,
            String lastErrorCode,
            Instant nextAttemptAt,
            Instant completedAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO post_media_deletion_plans (
                    post_id, original_storage_key, status, attempt_count,
                    last_error_code, next_attempt_at, completed_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_media_deletion_status), ?, ?, ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                postId,
                "chalkak/posts/dev/original/" + postId + ".webp",
                status,
                attemptCount,
                lastErrorCode,
                timestamp(nextAttemptAt),
                timestamp(completedAt)
        );
    }

    private DeletionPlanState findPlanState(UUID postId) {
        return jdbcTemplate.queryForObject("""
                SELECT CAST(status AS TEXT), attempt_count, last_error_code,
                       next_attempt_at, completed_at
                FROM post_media_deletion_plans
                WHERE post_id = ?
                """, (resultSet, rowNumber) -> new DeletionPlanState(
                resultSet.getString(1),
                resultSet.getInt(2),
                resultSet.getString(3),
                instant(resultSet.getTimestamp(4)),
                instant(resultSet.getTimestamp(5))
        ), postId);
    }

    private Timestamp timestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private record DeletionPlanState(
            String status,
            int attemptCount,
            String lastErrorCode,
            Instant nextAttemptAt,
            Instant completedAt
    ) {
    }
}
