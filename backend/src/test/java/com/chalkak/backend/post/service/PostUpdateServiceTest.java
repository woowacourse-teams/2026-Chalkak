package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostUpdateServiceTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576d1");

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        insertAuthor();
        insertOpenTopic();
        insertPhoto();
        insertPendingPost();
    }

    @Test
    @DisplayName("작성자가 열린 주제의 검수 대기 게시물 제목을 수정한다")
    void updatePost_pendingPost_updatesNormalizedTitleAndKeepsStatus() {
        // When
        var result = postCommandService.updatePost(AUTHOR_ID, POST_ID, "  수정 제목  ");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.postId()).isEqualTo(POST_ID);
        assertThat(result.title()).isEqualTo("수정 제목");

        Map<String, Object> updated = jdbcTemplate.queryForMap("""
                SELECT title, moderation_status
                FROM posts
                WHERE id = ?
                """, POST_ID);
        assertThat(updated.get("title")).isEqualTo("수정 제목");
        assertThat(updated.get("moderation_status").toString()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("승인 게시물 제목을 수정해도 검수 상태와 기존 시각 정보는 유지한다")
    void updatePost_approvedPost_updatesOnlyTitleAndUpdatedAt() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET moderation_status = CAST('APPROVED' AS moderation_status),
                    moderated_at = TIMESTAMPTZ '2026-08-28 10:10:00+00'
                WHERE id = ?
                """, POST_ID);
        Map<String, Object> beforeUpdate = findPostState();
        Instant beforeUpdatedAt = findUpdatedAt();

        // When
        var result = postCommandService.updatePost(AUTHOR_ID, POST_ID, "승인 제목");
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> afterUpdate = findPostState();
        assertThat(afterUpdate.get("title")).isEqualTo("승인 제목");
        assertThat(afterUpdate.get("moderation_status").toString()).isEqualTo("APPROVED");
        assertThat(afterUpdate.get("created_at")).isEqualTo(beforeUpdate.get("created_at"));
        assertThat(afterUpdate.get("moderated_at")).isEqualTo(beforeUpdate.get("moderated_at"));
        assertThat(findUpdatedAt()).isAfter(beforeUpdatedAt);
    }

    @Test
    @DisplayName("정규화한 제목이 기존 제목과 같으면 DB 수정 시각을 유지한다")
    void updatePost_sameNormalizedTitle_keepsUpdatedAt() {
        // Given
        Instant updatedAt = findUpdatedAt();

        // When
        var result = postCommandService.updatePost(AUTHOR_ID, POST_ID, "  기존 제목  ");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.title()).isEqualTo("기존 제목");
        assertThat(findUpdatedAt()).isEqualTo(updatedAt);
    }

    @Test
    @DisplayName("삭제된 게시물은 제목을 수정할 수 없다")
    void updatePost_deletedPost_throwsNotFoundException() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.updatePost(AUTHOR_ID, POST_ID, "수정 제목")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("탈퇴한 작성자의 인증 정보로 게시물 제목을 수정할 수 없다")
    void updatePost_withdrawnAuthor_throwsUnauthorizedException() {
        // Given
        jdbcTemplate.update(
                "UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                AUTHOR_ID
        );

        // When
        UnauthorizedException exception = catchThrowableOfType(
                UnauthorizedException.class,
                () -> postCommandService.updatePost(AUTHOR_ID, POST_ID, "수정 제목")
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception).hasMessage("유효하지 않은 인증 정보입니다.");
    }

    private void insertAuthor() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'post-update-author@example.com', CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/post-update/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, AUTHOR_ID);
    }

    private void insertOpenTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '게시물 수정', CURRENT_DATE,
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
                    ?, 'chalkak/posts/update/original.webp',
                    'chalkak/posts/update/thumbnail.webp', CAST('{}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
    }

    private void insertPendingPost() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '기존 제목', CAST('PENDING' AS moderation_status),
                    TIMESTAMPTZ '2026-08-28 10:00:00+00',
                    TIMESTAMPTZ '2026-08-28 10:00:00+00'
                )
                """, POST_ID, AUTHOR_ID, TOPIC_ID, PHOTO_ID);
    }

    private Map<String, Object> findPostState() {
        return jdbcTemplate.queryForMap("""
                SELECT title, moderation_status, created_at, updated_at, moderated_at
                FROM posts
                WHERE id = ?
                """, POST_ID);
    }

    private Instant findUpdatedAt() {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM posts WHERE id = ?",
                Instant.class,
                POST_ID
        );
    }
}
