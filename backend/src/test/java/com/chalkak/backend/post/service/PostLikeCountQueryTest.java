package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.admin.repository.post.AdminPostSort;
import com.chalkak.backend.admin.service.post.AdminPostListResult;
import com.chalkak.backend.admin.service.post.AdminPostQueryService;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostLikeCountQueryTest extends IntegrationTestSupport {

    private static final LocalDate TOPIC_DATE = LocalDate.of(
            2026,
            8,
            12
    );
    private static final UUID TOPIC_ID = UUID.randomUUID();
    private static final UUID FIRST_USER_ID = UUID.randomUUID();
    private static final UUID SECOND_USER_ID = UUID.randomUUID();
    private static final UUID FIRST_POST_ID = UUID.randomUUID();
    private static final UUID SECOND_POST_ID = UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private AdminPostQueryService adminPostQueryService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO topics (id, title, topic_date, starts_at, ends_at)
                VALUES (?, '집계 조회 검증', '2026-08-12', '2026-08-12T00:00:00Z', '2026-08-13T00:00:00Z')
                """, TOPIC_ID);
        insertPost(FIRST_USER_ID, FIRST_POST_ID);
        insertPost(SECOND_USER_ID, SECOND_POST_ID);
        // 조회 경로를 구분하려고 원본 관계와 다른 값을 의도적으로 준비한다.
        // 운영에서는 관계와 집계가 항상 일치해야 한다.
        jdbcTemplate.update(
                """
                        INSERT INTO post_like_counts (post_id, like_count) VALUES (?, 7), (?, 19)
                        """,
                FIRST_POST_ID,
                SECOND_POST_ID
        );
        jdbcTemplate.update(
                """
                        INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)
                        """,
                FIRST_POST_ID,
                SECOND_USER_ID
        );
    }

    @ParameterizedTest
    @EnumSource(PostSort.class)
    @DisplayName("모든 정렬의 목록은 집계 값을 읽고 내 좋아요 여부는 관계에서 확인한다")
    void getPosts_everySort_readsStoredCountsAndRelationState(PostSort sort) {
        // When
        PostListResult result = postQueryService.getPosts(
                TOPIC_DATE,
                sort,
                null,
                1,
                20,
                Optional.of(SECOND_USER_ID)
        );

        // Then
        assertThat(result.posts()).hasSize(2);
        for (PostListResult.PostSummary post : result.posts()) {
            if (post.id().equals(FIRST_POST_ID)) {
                assertThat(post.likeCount()).isEqualTo(7);
                assertThat(post.isLiked()).isTrue();
                continue;
            }
            assertThat(post.likeCount()).isEqualTo(19);
            assertThat(post.isLiked()).isFalse();
        }
        if (sort == PostSort.POPULAR) {
            assertThat(result.posts().getFirst().id()).isEqualTo(SECOND_POST_ID);
        }
    }

    @Test
    @DisplayName("게시물 상세는 집계 값을 읽고 내 좋아요 여부는 관계에서 확인한다")
    void getPost_existingPost_readsStoredCount() {
        // When
        PostDetail result = postQueryService.getPost(FIRST_POST_ID, SECOND_USER_ID);

        // Then
        assertThat(result.likeCount()).isEqualTo(7);
        assertThat(result.isLiked()).isTrue();
    }

    @Test
    @DisplayName("작성자의 사진 목록도 같은 집계 값을 사용한다")
    void getAuthorPosts_existingAuthor_readsStoredCount() {
        // When
        AuthorPostListResult result = postQueryService.getAuthorPosts(
                FIRST_USER_ID,
                1,
                20,
                SECOND_USER_ID
        );

        // Then
        assertThat(result.posts().getFirst().likeCount()).isEqualTo(7);
        assertThat(result.posts().getFirst().isLiked()).isTrue();
    }

    @Test
    @DisplayName("관리자 목록과 상세도 동일한 집계 값을 사용한다")
    void getAdminPosts_existingPosts_readsStoredCounts() {
        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                TOPIC_ID,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.posts()).hasSize(2);
        for (var post : result.posts()) {
            long expected = post.postId().equals(FIRST_POST_ID) ? 7 : 19;
            assertThat(post.likeCount()).isEqualTo(expected);
        }
        assertThat(adminPostQueryService.getPost(FIRST_POST_ID).likeCount()).isEqualTo(7);
    }

    @ParameterizedTest
    @EnumSource(PostSort.class)
    @DisplayName("목록의 집계 행이 누락되어도 게시물을 조용히 제외하거나 0으로 대체하지 않는다")
    void getPosts_missingCounter_throwsIntegrityException(PostSort sort) {
        // Given
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", FIRST_POST_ID);

        // When & Then
        assertThatThrownBy(() -> postQueryService.getPosts(
                TOPIC_DATE,
                sort,
                null,
                1,
                20,
                Optional.of(SECOND_USER_ID)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("관리자 목록은 집계 행 누락 시 게시물 ID를 포함한 명확한 오류를 낸다")
    void getAdminPosts_missingCounter_throwsIntegrityException() {
        // Given
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", FIRST_POST_ID);

        // When & Then
        assertThatThrownBy(() -> adminPostQueryService.getPosts(
                null,
                TOPIC_ID,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null,
                1,
                20
        )).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("좋아요 집계 행을 찾을 수 없습니다: " + FIRST_POST_ID);
    }

    @Test
    @DisplayName("관리자 상세는 집계 행 누락 시 게시물 ID를 포함한 명확한 오류를 낸다")
    void getAdminPost_missingCounter_throwsIntegrityException() {
        // Given
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", FIRST_POST_ID);

        // When & Then
        assertThatThrownBy(() -> adminPostQueryService.getPost(FIRST_POST_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("좋아요 집계 행을 찾을 수 없습니다: " + FIRST_POST_ID);
    }

    @Test
    @DisplayName("관리자 목록은 집계 행의 실제 값이 0이면 정상적으로 0을 반환한다")
    void getAdminPosts_zeroCounter_returnsZero() {
        // Given
        jdbcTemplate.update("UPDATE post_like_counts SET like_count = 0 WHERE post_id = ?",
                FIRST_POST_ID);

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                TOPIC_ID,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                null,
                null,
                1,
                20
        );

        // Then
        assertThat(result.posts()).hasSize(2);
        boolean foundZeroCounter = false;
        for (var post : result.posts()) {
            if (post.postId().equals(FIRST_POST_ID)) {
                assertThat(post.likeCount()).isZero();
                foundZeroCounter = true;
            }
        }
        assertThat(foundZeroCounter).isTrue();
    }

    @Test
    @DisplayName("관리자 상세는 집계 행의 실제 값이 0이면 정상적으로 0을 반환한다")
    void getAdminPost_zeroCounter_returnsZero() {
        // Given
        jdbcTemplate.update("UPDATE post_like_counts SET like_count = 0 WHERE post_id = ?",
                FIRST_POST_ID);

        // When & Then
        assertThat(adminPostQueryService.getPost(FIRST_POST_ID).likeCount()).isZero();
    }

    private void insertPost(UUID userId, UUID postId) {
        jdbcTemplate.update(
                "INSERT INTO users (id, email, signature_original_storage_key) VALUES (?, ?, ?)",
                userId,
                userId + "@count-query.test",
                "chalkak/test/signatures/" + userId
        );
        jdbcTemplate.update(
                "INSERT INTO photos (id, original_storage_key) VALUES (?, ?)",
                postId,
                "chalkak/test/posts/" + postId
        );
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, moderation_status)
                        VALUES (?, ?, ?, ?, 'APPROVED')
                        """,
                postId,
                userId,
                TOPIC_ID,
                postId
        );
    }
}
