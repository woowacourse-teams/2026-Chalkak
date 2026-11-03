package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostUpdateServiceTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576a1");
    private static final UUID TOPIC_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576b1");
    private static final UUID PHOTO_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576c1");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6576d1");

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
    @DisplayName("삭제된 게시물은 수정할 수 없다")
    void updatePost_deletedPost_throwsNotFoundException() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                POST_ID);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.updatePost(
                        AUTHOR_ID,
                        POST_ID,
                        "수정 제목",
                        null,
                        false,
                        null
                ));

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("작성자가 열린 주제의 검수 대기 게시물 제목을 수정한다")
    void updatePost_pendingPost_updatesNormalizedTitleAndKeepsStatus() {
        // When
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "  수정 제목  ",
                null,
                false,
                null
        );
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
    @DisplayName("미동의 게시물을 동의와 ID로 수정하면 정규화한 ID와 동의 시각을 저장하고 응답한다")
    void updatePost_agreeWithId_savesNormalizedConsent() {
        // When
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "수정 제목",
                null,
                true,
                " @Chalkak "
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.instagramUploadAgreed()).isTrue();
        assertThat(result.instagramId()).isEqualTo("chalkak");
        Map<String, Object> saved = findInstagramState();
        assertThat(saved.get("instagram_upload_agreed")).isEqualTo(true);
        assertThat(saved.get("instagram_id")).isEqualTo("chalkak");
        assertThat(saved.get("instagram_agreed_at")).isNotNull();
        assertThat(saved.get("instagram_revoked_at")).isNull();
    }

    @Test
    @DisplayName("동의한 게시물을 동의 필드 없이 수정하면 철회하고 ID를 지우며 철회 시각을 저장한다")
    void updatePost_omittedConsent_revokesAndClearsId() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET instagram_upload_agreed = TRUE,
                    instagram_id = 'chalkak',
                    instagram_agreed_at = TIMESTAMPTZ '2026-08-28 10:00:00+00'
                WHERE id = ?
                """, POST_ID);

        // When
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "수정 제목",
                null,
                false,
                null
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.instagramUploadAgreed()).isFalse();
        assertThat(result.instagramId()).isNull();
        Map<String, Object> saved = findInstagramState();
        assertThat(saved.get("instagram_upload_agreed")).isEqualTo(false);
        assertThat(saved.get("instagram_id")).isNull();
        assertThat(saved.get("instagram_agreed_at")).isNotNull();
        assertThat(saved.get("instagram_revoked_at")).isNotNull();
    }

    @Test
    @DisplayName("동의 상태에서 ID만 바꿔 수정하면 동의 시각은 그대로 둔다")
    void updatePost_sameAgreementDifferentId_keepsAgreedAt() {
        // Given
        jdbcTemplate.update("""
                UPDATE posts
                SET instagram_upload_agreed = TRUE,
                    instagram_id = 'chalkak',
                    instagram_agreed_at = TIMESTAMPTZ '2026-08-28 10:00:00+00'
                WHERE id = ?
                """, POST_ID);

        // When
        postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "수정 제목",
                null,
                true,
                "other.id"
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> saved = findInstagramState();
        assertThat(saved.get("instagram_id")).isEqualTo("other.id");
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT instagram_agreed_at = TIMESTAMPTZ '2026-08-28 10:00:00+00'
                        FROM posts
                        WHERE id = ?
                        """,
                Boolean.class,
                POST_ID
        )).isTrue();
        assertThat(saved.get("instagram_revoked_at")).isNull();
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
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "승인 제목",
                null,
                false,
                null
        );
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
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                "  기존 제목  ",
                null,
                false,
                null
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.title()).isEqualTo("기존 제목");
        assertThat(findUpdatedAt()).isEqualTo(updatedAt);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("null, 빈 문자열, 공백 제목으로 수정하면 DB에 NULL로 저장한다")
    void updatePost_emptyTitle_storesNullTitle(String title) {
        // When
        var result = postCommandService.updatePost(
                AUTHOR_ID,
                POST_ID,
                title,
                null,
                false,
                null
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.title()).isNull();

        Map<String, Object> updated = jdbcTemplate.queryForMap("""
                SELECT title
                FROM posts
                WHERE id = ?
                """, POST_ID);
        assertThat(updated.get("title")).isNull();
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
        jdbcTemplate.update(
                """
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, title,
                            moderation_status, created_at, updated_at
                        ) VALUES (
                            ?, ?, ?, ?, '기존 제목', CAST('PENDING' AS moderation_status),
                            TIMESTAMPTZ '2026-08-28 10:00:00+00',
                            TIMESTAMPTZ '2026-08-28 10:00:00+00'
                        )
                        """,
                POST_ID,
                AUTHOR_ID,
                TOPIC_ID,
                PHOTO_ID
        );
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

    private Map<String, Object> findInstagramState() {
        return jdbcTemplate.queryForMap("""
                SELECT instagram_upload_agreed, instagram_id,
                       instagram_agreed_at, instagram_revoked_at
                FROM posts
                WHERE id = ?
                """, POST_ID);
    }
}
