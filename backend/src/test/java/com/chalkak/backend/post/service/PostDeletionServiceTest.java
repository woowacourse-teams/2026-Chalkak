package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostDeletionServiceTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574a1");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574a2");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574d1");
    private static final UUID UNKNOWN_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574df");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6574e1");
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertAuthor();
        insertTopic();
        insertPhoto();
        insertUpload();
        insertPendingPost();
    }

    @Test
    @DisplayName("작성자가 검수 대기 게시물을 삭제하면 게시물과 사진을 soft delete하고 이미지 경로를 보존한다")
    void deletePost_pendingPost_softDeletesPostAndPhotoAndPreservesStorageKeys() {
        // When
        postCommandService.deletePost(AUTHOR_ID, POST_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> deleted = jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at,
                       photo.original_storage_key,
                       photo.thumbnail_storage_key
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
        assertThat(deleted.get("post_deleted_at")).isNotNull();
        assertThat(deleted.get("photo_deleted_at"))
                .isEqualTo(deleted.get("post_deleted_at"));
        assertThat(deleted.get("original_storage_key")).isEqualTo(ORIGINAL_STORAGE_KEY);
        assertThat(deleted.get("thumbnail_storage_key")).isEqualTo(THUMBNAIL_STORAGE_KEY);
    }

    @Test
    @DisplayName("작성자가 승인 게시물을 삭제하면 게시물과 사진을 soft delete하고 좋아요와 조회 결과에서 제거한다")
    void deletePost_approvedPost_softDeletesAndDeletesLikesAndExcludesReadModels() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET moderation_status = CAST('APPROVED' AS moderation_status),
                    moderated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, POST_ID);
        jdbcTemplate.update("""
                INSERT INTO post_likes (post_id, user_id)
                VALUES (?, ?)
                """, POST_ID, AUTHOR_ID);

        // When
        postCommandService.deletePost(AUTHOR_ID, POST_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> deleted = jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
        assertThat(deleted.get("post_deleted_at")).isNotNull();
        assertThat(deleted.get("photo_deleted_at"))
                .isEqualTo(deleted.get("post_deleted_at"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isZero();
        assertThat(postRepository.findVisibleById(POST_ID)).isEmpty();
        assertThat(postRepository.findCalendarPostsByAuthorIdAndTopicDateBetween(
                AUTHOR_ID,
                LocalDate.now(),
                LocalDate.now()
        )).isEmpty();
        assertThat(postRepository.findVisibleRecentByTopicId(TOPIC_ID, 0, 20).posts())
                .isEmpty();
    }

    @Test
    @DisplayName("다른 사용자가 승인 게시물을 삭제하면 거부하고 게시물과 사진과 좋아요를 유지한다")
    void deletePost_otherUser_throwsForbiddenExceptionAndKeepsEveryState() {
        // Given
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'other-post-deletion@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/other-post-deletion/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, OTHER_USER_ID);
        jdbcTemplate.update("""
                UPDATE posts
                SET moderation_status = CAST('APPROVED' AS moderation_status),
                    moderated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, POST_ID);
        jdbcTemplate.update("""
                INSERT INTO post_likes (post_id, user_id)
                VALUES (?, ?)
                """, POST_ID, AUTHOR_ID);

        // When
        ForbiddenException exception = catchThrowableOfType(
                ForbiddenException.class,
                () -> postCommandService.deletePost(OTHER_USER_ID, POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
        Map<String, Object> deletionState = jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
        assertThat(deletionState.get("post_deleted_at")).isNull();
        assertThat(deletionState.get("photo_deleted_at")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isOne();
    }

    @Test
    @DisplayName("이미지 처리 중인 게시물은 삭제를 거부하고 게시물과 사진 상태를 유지한다")
    void deletePost_validatingPost_throwsBusinessExceptionAndKeepsPostAndPhoto() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET moderation_status = CAST('VALIDATING' AS moderation_status)
                WHERE id = ?
                """, POST_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.deletePost(AUTHOR_ID, POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("이미지 처리 중인 게시물은 삭제할 수 없습니다.");
        Map<String, Object> deletionState = jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
        assertThat(deletionState.get("post_deleted_at")).isNull();
        assertThat(deletionState.get("photo_deleted_at")).isNull();
    }

    @Test
    @DisplayName("검수 거절 게시물은 삭제를 거부하고 게시물과 사진 상태를 유지한다")
    void deletePost_rejectedPost_throwsBusinessExceptionAndKeepsPostAndPhoto() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET moderation_status = CAST('REJECTED' AS moderation_status),
                    moderated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, POST_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.deletePost(AUTHOR_ID, POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("검수 거절된 게시물은 삭제할 수 없습니다.");
        Map<String, Object> deletionState = jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
        assertThat(deletionState.get("post_deleted_at")).isNull();
        assertThat(deletionState.get("photo_deleted_at")).isNull();
    }

    @Test
    @DisplayName("삭제 요청을 반복해도 게시물과 사진의 최초 삭제 시각을 유지한다")
    void deletePost_alreadyDeletedPost_keepsFirstDeletionTime() {
        // Given
        postCommandService.deletePost(AUTHOR_ID, POST_ID);
        entityManager.flush();
        entityManager.clear();
        jdbcTemplate.update("""
                UPDATE posts
                SET deleted_at = TIMESTAMPTZ '2026-08-28 10:00:00+00'
                WHERE id = ?
                """, POST_ID);
        jdbcTemplate.update("""
                UPDATE photos
                SET deleted_at = TIMESTAMPTZ '2026-08-28 10:00:00+00'
                WHERE id = ?
                """, PHOTO_ID);
        Map<String, Object> firstDeletion = findDeletionState();

        // When
        postCommandService.deletePost(AUTHOR_ID, POST_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> repeatedDeletion = findDeletionState();
        assertThat(repeatedDeletion.get("post_deleted_at"))
                .isEqualTo(firstDeletion.get("post_deleted_at"));
        assertThat(repeatedDeletion.get("photo_deleted_at"))
                .isEqualTo(firstDeletion.get("photo_deleted_at"));
    }

    @Test
    @DisplayName("존재하지 않는 게시물은 삭제할 수 없다")
    void deletePost_unknownPost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.deletePost(AUTHOR_ID, UNKNOWN_POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    private void insertAuthor() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'post-author-deletion@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/post-deletion/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, AUTHOR_ID);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '작성자 게시물 삭제', CURRENT_DATE,
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

    private void insertUpload() {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, image_metadata,
                    expires_at, claimed_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('READY' AS post_image_upload_status), CAST('{}' AS jsonb),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UPLOAD_ID, AUTHOR_ID);
    }

    private void insertPendingPost() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, '삭제 대상', CAST('PENDING' AS moderation_status),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, AUTHOR_ID, TOPIC_ID, PHOTO_ID, UPLOAD_ID);
    }

    private Map<String, Object> findDeletionState() {
        return jdbcTemplate.queryForMap("""
                SELECT post.deleted_at AS post_deleted_at,
                       photo.deleted_at AS photo_deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, POST_ID);
    }
}
