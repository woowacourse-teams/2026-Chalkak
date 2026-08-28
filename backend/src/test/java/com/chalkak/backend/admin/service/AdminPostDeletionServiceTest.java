package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class AdminPostDeletionServiceTest extends IntegrationTestSupport {

    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573f1");
    private static final UUID UNKNOWN_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573ff");
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573c1");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573e1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6573d1");
    private static final String SIGNATURE_STORAGE_KEY =
            "chalkak/signatures/deletion-service/original.webp";
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";

    @Autowired
    private AdminPostDeletionService adminPostDeletionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin();
        insertUser();
        insertTopic();
        insertPhoto();
        insertUpload("READY");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @ParameterizedTest
    @EnumSource(
            value = ModerationStatus.class,
            names = {"PENDING", "APPROVED", "REJECTED"}
    )
    @DisplayName("허용된 검수 상태의 게시물을 삭제하면 미디어를 보존하고 soft delete와 감사 로그를 저장한다")
    void deletePost_allowedStatus_softDeletesAndPreservesMediaAndCreatesAudit(
            ModerationStatus moderationStatus
    ) {
        // Given
        insertPost(moderationStatus);

        // When
        adminPostDeletionService.deletePost(
                POST_ID,
                ADMIN_ID,
                "  운영 정책 위반  "
        );

        // Then
        SoftDeletionRow softDeletion = findSoftDeletion();
        DeletionAuditRow audit = findSingleAudit();
        assertThat(softDeletion.postDeletedAt()).isNotNull();
        assertThat(softDeletion.photoDeletedAt()).isEqualTo(softDeletion.postDeletedAt());
        assertThat(softDeletion.originalStorageKey()).isEqualTo(ORIGINAL_STORAGE_KEY);
        assertThat(softDeletion.thumbnailStorageKey()).isEqualTo(THUMBNAIL_STORAGE_KEY);
        assertThat(audit.action()).isEqualTo("POST_DELETED");
        assertThat(audit.actorAdminId()).isEqualTo(ADMIN_ID);
        assertThat(audit.reason()).isEqualTo("운영 정책 위반");
        assertThat(audit.beforeStatus()).isEqualTo(moderationStatus.name());
        assertThat(audit.beforeDeletedAt()).isNull();
        assertThat(audit.afterStatus()).isEqualTo(moderationStatus.name());
        Instant auditDeletedAt = Instant.parse(audit.afterDeletedAt())
                .truncatedTo(ChronoUnit.MICROS);
        assertThat(auditDeletedAt).isCloseTo(
                softDeletion.postDeletedAt(),
                within(1, ChronoUnit.MICROS)
        );
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 관리자가 삭제할 수 없다")
    void deletePost_validatingPost_throwsStateInvalidException() {
        // Given
        insertPost(ModerationStatus.VALIDATING);

        // When
        BusinessException exception = catchThrowableOfType(
                () -> adminPostDeletionService.deletePost(
                        POST_ID,
                        ADMIN_ID,
                        "운영 정책 위반"
                ),
                BusinessException.class
        );

        // Then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.POST_DELETION_STATE_INVALID);
        SoftDeletionRow softDeletion = findSoftDeletion();
        assertThat(softDeletion.postDeletedAt()).isNull();
        assertThat(softDeletion.photoDeletedAt()).isNull();
        assertThat(countAudits()).isZero();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("존재하지 않는 게시물은 삭제할 수 없다")
    void deletePost_unknownPost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                () -> adminPostDeletionService.deletePost(
                        POST_ID,
                        ADMIN_ID,
                        "운영 정책 위반"
                ),
                NotFoundException.class
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(countAudits()).isZero();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제 요청을 다시 보내도 최초 삭제 시각과 감사 로그를 유지한다")
    void deletePost_alreadyDeletedPost_keepsFirstDeletionAndAudit() {
        // Given
        insertPost(ModerationStatus.APPROVED);
        adminPostDeletionService.deletePost(POST_ID, ADMIN_ID, "최초 삭제 사유");
        SoftDeletionRow firstDeletion = findSoftDeletion();

        // When
        adminPostDeletionService.deletePost(POST_ID, ADMIN_ID, "중복 삭제 사유");

        // Then
        SoftDeletionRow repeatedDeletion = findSoftDeletion();
        assertThat(repeatedDeletion.postDeletedAt())
                .isEqualTo(firstDeletion.postDeletedAt());
        assertThat(repeatedDeletion.photoDeletedAt())
                .isEqualTo(firstDeletion.photoDeletedAt());
        assertThat(repeatedDeletion.originalStorageKey()).isEqualTo(ORIGINAL_STORAGE_KEY);
        assertThat(repeatedDeletion.thumbnailStorageKey())
                .isEqualTo(THUMBNAIL_STORAGE_KEY);
        assertThat(countAudits()).isEqualTo(1);
        assertThat(findSingleAudit().reason()).isEqualTo("최초 삭제 사유");
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("감사 로그 관리자 외래키 저장이 실패하면 게시물과 사진 삭제도 롤백한다")
    void deletePost_auditActorForeignKeyFailure_rollsBackEveryChange() {
        // Given
        insertPost(ModerationStatus.REJECTED);

        // When
        DataIntegrityViolationException exception = catchThrowableOfType(
                () -> adminPostDeletionService.deletePost(
                        POST_ID,
                        UNKNOWN_ADMIN_ID,
                        "운영 정책 위반"
                ),
                DataIntegrityViolationException.class
        );

        // Then
        assertThat(exception).isNotNull();
        SoftDeletionRow softDeletion = findSoftDeletion();
        assertThat(softDeletion.postDeletedAt()).isNull();
        assertThat(softDeletion.photoDeletedAt()).isNull();
        assertThat(countAudits()).isZero();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제된 게시물은 사용자 상세·캘린더·피드 조회에서 즉시 제외한다")
    void deletePost_approvedPost_excludesEveryUserReadModel() {
        // Given
        insertPost(ModerationStatus.APPROVED);

        // When
        adminPostDeletionService.deletePost(POST_ID, ADMIN_ID, "운영 정책 위반");

        // Then
        LocalDate topicDate = LocalDate.now();
        assertThat(postRepository.findVisibleById(POST_ID)).isEmpty();
        assertThat(postRepository.findCalendarPostsByAuthorIdAndTopicDateBetween(
                USER_ID,
                topicDate,
                topicDate
        )).isEmpty();
        assertThat(postRepository.findVisibleRecentByTopicId(TOPIC_ID, 0, 20).posts())
                .isEmpty();
    }

    private SoftDeletionRow findSoftDeletion() {
        return jdbcTemplate.queryForObject("""
                SELECT post.deleted_at, photo.deleted_at,
                       photo.original_storage_key, photo.thumbnail_storage_key
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, (resultSet, rowNumber) -> new SoftDeletionRow(
                instant(resultSet.getTimestamp(1)),
                instant(resultSet.getTimestamp(2)),
                resultSet.getString(3),
                resultSet.getString(4)
        ), POST_ID);
    }

    private DeletionAuditRow findSingleAudit() {
        return jdbcTemplate.queryForObject("""
                SELECT CAST(action AS TEXT), actor_admin_id, reason,
                       before_state ->> 'moderationStatus',
                       before_state ->> 'deletedAt',
                       after_state ->> 'moderationStatus',
                       after_state ->> 'deletedAt'
                FROM admin_audit_logs
                WHERE target_type = CAST('POST' AS admin_target_type)
                  AND target_id = ?
                  AND action = CAST('POST_DELETED' AS admin_action)
                """, (resultSet, rowNumber) -> new DeletionAuditRow(
                resultSet.getString(1),
                resultSet.getObject(2, UUID.class),
                resultSet.getString(3),
                resultSet.getString(4),
                resultSet.getString(5),
                resultSet.getString(6),
                resultSet.getString(7)
        ), POST_ID);
    }

    private int countAudits() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM admin_audit_logs
                WHERE target_id = ?
                  AND action = CAST('POST_DELETED' AS admin_action)
                """, Integer.class, POST_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM post_image_uploads WHERE id = ?", UPLOAD_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update(
                "DELETE FROM admins WHERE id IN (?, ?)",
                ADMIN_ID,
                UNKNOWN_ADMIN_ID
        );
    }

    private void insertAdmin() {
        jdbcTemplate.update("""
                INSERT INTO admins (
                    id, username, password, created_at, updated_at
                ) VALUES (
                    ?, 'post-deletion-admin', 'test-password',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, ADMIN_ID);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'post-deletion@example.com', CAST('ACTIVE' AS user_status), ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID, SIGNATURE_STORAGE_KEY);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '관리자 게시물 삭제', CURRENT_DATE,
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
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, CAST('{}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

    private void insertPost(ModerationStatus moderationStatus) {
        Instant moderatedAt = moderationStatus == ModerationStatus.PENDING
                || moderationStatus == ModerationStatus.VALIDATING
                ? null
                : Instant.parse("2026-08-28T03:30:00Z");
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status, moderated_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, '삭제 대상', CAST(? AS moderation_status), ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                POST_ID,
                USER_ID,
                TOPIC_ID,
                PHOTO_ID,
                UPLOAD_ID,
                moderationStatus.name(),
                timestamp(moderatedAt)
        );
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

    private record SoftDeletionRow(
            Instant postDeletedAt,
            Instant photoDeletedAt,
            String originalStorageKey,
            String thumbnailStorageKey
    ) {
    }

    private record DeletionAuditRow(
            String action,
            UUID actorAdminId,
            String reason,
            String beforeStatus,
            String beforeDeletedAt,
            String afterStatus,
            String afterDeletedAt
    ) {
    }
}
