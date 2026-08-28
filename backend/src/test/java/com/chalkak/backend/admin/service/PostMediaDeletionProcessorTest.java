package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PostMediaDeletionProcessorTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574c1");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574e1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574d1");
    private static final UUID PLAN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574e2");
    private static final String STAGING_STORAGE_KEY =
            "chalkak/staging/test/posts/" + UPLOAD_ID + ".webp";
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";

    @Autowired
    private PostMediaDeletionProcessor postMediaDeletionProcessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertUser();
        insertTopic();
        insertPhoto();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @ParameterizedTest
    @ValueSource(strings = {"READY", "REJECTED"})
    @DisplayName("처리가 끝난 업로드의 세 게시물 객체를 삭제하면 계획을 성공으로 완료한다")
    void process_processedUpload_deletesEveryObjectAndSucceeds(String uploadStatus) {
        // Given
        insertUpload(uploadStatus);
        insertDeletedPost(UPLOAD_ID);
        insertPlan(UPLOAD_ID, STAGING_STORAGE_KEY);

        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState state = findPlanState();
        assertThat(state.status()).isEqualTo("SUCCEEDED");
        assertThat(state.attemptCount()).isEqualTo(1);
        assertThat(state.lastErrorCode()).isNull();
        assertThat(state.nextAttemptAt()).isNull();
        assertThat(state.completedAt()).isNotNull();
        then(postImageStorage).should().deleteImage(STAGING_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(THUMBNAIL_STORAGE_KEY);
    }

    @Test
    @DisplayName("일부 객체 삭제가 실패해도 나머지를 시도하고 안전한 오류 코드로 재시도를 예약한다")
    void process_partialStorageFailure_failsSafelyAndRetrySucceeds() {
        // Given
        insertUpload("READY");
        insertDeletedPost(UPLOAD_ID);
        insertPlan(UPLOAD_ID, STAGING_STORAGE_KEY);
        willThrow(new IllegalStateException(
                "AWS secret=raw-secret key=" + ORIGINAL_STORAGE_KEY
        )).willDoNothing().given(postImageStorage).deleteImage(ORIGINAL_STORAGE_KEY);
        Instant firstAttemptStartedAt = Instant.now();

        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState failed = findPlanState();
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.attemptCount()).isEqualTo(1);
        assertThat(failed.lastErrorCode()).isEqualTo("STORAGE_DELETE_FAILED");
        assertThat(failed.lastErrorCode())
                .doesNotContain("raw-secret", ORIGINAL_STORAGE_KEY, "AWS");
        assertThat(failed.nextAttemptAt()).isAfter(firstAttemptStartedAt);
        assertThat(failed.completedAt()).isNull();
        then(postImageStorage).should().deleteImage(STAGING_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(THUMBNAIL_STORAGE_KEY);

        // Given
        jdbcTemplate.update("""
                UPDATE post_media_deletion_plans
                SET next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE post_id = ?
                """, POST_ID);

        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState succeeded = findPlanState();
        assertThat(succeeded.status()).isEqualTo("SUCCEEDED");
        assertThat(succeeded.attemptCount()).isEqualTo(2);
        assertThat(succeeded.lastErrorCode()).isNull();
        assertThat(succeeded.nextAttemptAt()).isNull();
        assertThat(succeeded.completedAt()).isNotNull();
        then(postImageStorage).should(times(2)).deleteImage(STAGING_STORAGE_KEY);
        then(postImageStorage).should(times(2)).deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should(times(2)).deleteImage(THUMBNAIL_STORAGE_KEY);
    }

    @Test
    @DisplayName("writer 유예가 끝난 계획은 업로드 상태가 남아 있어도 객체를 지우고 완료한다")
    void process_dueIssuedUpload_deletesEveryObjectAndSucceeds() {
        // Given
        insertUpload("ISSUED");
        insertDeletedPost(UPLOAD_ID);
        insertPlan(UPLOAD_ID, STAGING_STORAGE_KEY);
        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState state = findPlanState();
        assertThat(state.status()).isEqualTo("SUCCEEDED");
        assertThat(state.attemptCount()).isEqualTo(1);
        assertThat(state.lastErrorCode()).isNull();
        assertThat(state.nextAttemptAt()).isNull();
        assertThat(state.completedAt()).isNotNull();
        then(postImageStorage).should().deleteImage(STAGING_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(THUMBNAIL_STORAGE_KEY);
    }

    @Test
    @DisplayName("다른 작업자가 이미 미래로 미룬 계획은 오래된 due 목록으로 다시 처리하지 않는다")
    void process_futurePlan_doesNothing() {
        // Given
        insertUpload("READY");
        insertDeletedPost(UPLOAD_ID);
        insertPlan(UPLOAD_ID, STAGING_STORAGE_KEY);
        Instant futureAttemptAt = Instant.now().plusSeconds(600);
        jdbcTemplate.update(
                "UPDATE post_media_deletion_plans SET next_attempt_at = ? WHERE post_id = ?",
                Timestamp.from(futureAttemptAt),
                POST_ID
        );

        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState state = findPlanState();
        assertThat(state.status()).isEqualTo("PENDING");
        assertThat(state.attemptCount()).isZero();
        assertThat(state.nextAttemptAt()).isCloseTo(
                futureAttemptAt,
                within(1, ChronoUnit.MICROS)
        );
        then(postImageStorage).should(never()).deleteImage(STAGING_STORAGE_KEY);
        then(postImageStorage).should(never()).deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should(never()).deleteImage(THUMBNAIL_STORAGE_KEY);
    }

    @Test
    @DisplayName("업로드 연결이 없는 기존 게시물은 사진 원본과 썸네일만 삭제한다")
    void process_postWithoutUpload_deletesPhotoObjectsAndSucceeds() {
        // Given
        insertDeletedPost(null);
        insertPlan(null, null);

        // When
        postMediaDeletionProcessor.process(POST_ID);

        // Then
        DeletionPlanState state = findPlanState();
        assertThat(state.status()).isEqualTo("SUCCEEDED");
        assertThat(state.attemptCount()).isEqualTo(1);
        assertThat(state.completedAt()).isNotNull();
        then(postImageStorage).should().deleteImage(ORIGINAL_STORAGE_KEY);
        then(postImageStorage).should().deleteImage(THUMBNAIL_STORAGE_KEY);
        then(postImageStorage).shouldHaveNoMoreInteractions();
    }

    private DeletionPlanState findPlanState() {
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
        ), POST_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM post_media_deletion_plans WHERE post_id = ?",
                POST_ID
        );
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM post_image_uploads WHERE id = ?", UPLOAD_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'media-deletion@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/media-deletion/original.webp',
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
                    ?, '미디어 삭제 처리', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
    }

    private void insertPhoto() {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, CAST('{}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID, ORIGINAL_STORAGE_KEY, THUMBNAIL_STORAGE_KEY);
    }

    private void insertUpload(String status) {
        String rejectionReason = status.equals("REJECTED") ? "PROCESSING_ERROR" : null;
        String imageMetadata = status.equals("READY") ? "{}" : null;
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, rejection_reason, image_metadata,
                    expires_at, claimed_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_image_upload_status), ?, CAST(? AS jsonb),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                UPLOAD_ID,
                USER_ID,
                status,
                rejectionReason,
                imageMetadata
        );
    }

    private void insertDeletedPost(UUID uploadId) {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status, moderated_at,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, ?, '미디어 삭제', CAST('APPROVED' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID, uploadId);
    }

    private void insertPlan(UUID uploadId, String stagingStorageKey) {
        jdbcTemplate.update("""
                INSERT INTO post_media_deletion_plans (
                    id, post_id, post_image_upload_id,
                    staging_storage_key, original_storage_key, thumbnail_storage_key,
                    status, attempt_count, last_error_code,
                    next_attempt_at, completed_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?,
                    CAST('PENDING' AS post_media_deletion_status), 0, NULL,
                    CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                PLAN_ID,
                POST_ID,
                uploadId,
                stagingStorageKey,
                ORIGINAL_STORAGE_KEY,
                THUMBNAIL_STORAGE_KEY
        );
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
