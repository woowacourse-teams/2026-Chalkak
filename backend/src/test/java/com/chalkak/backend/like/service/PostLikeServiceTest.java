package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostLikeServiceTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID USER_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
    private static final UUID UNKNOWN_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a3");
    private static final UUID BANNED_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a4");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES
                    (
                        '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                        'post-like-author@example.com',
                        'ACTIVE',
                        'chalkak/dev/signatures/post-like-author.png',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    ),
                    (
                        '0198f6c1-62ba-7d30-8b12-0f733b6570a2',
                        'post-like-user@example.com',
                        'ACTIVE',
                        'chalkak/dev/signatures/post-like-user.png',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    ),
                    (
                        '0198f6c1-62ba-7d30-8b12-0f733b6570a4',
                        'post-like-banned@example.com',
                        'BANNED',
                        'chalkak/dev/signatures/post-like-banned.png',
                        CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP
                    )
                """);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '오늘 가장 기억에 남은 순간',
                    '2026-08-12',
                    '2026-08-12T00:00:00Z',
                    '2026-08-13T00:00:00Z',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c3',
                    'chalkak/dev/posts/post-like-service.jpg',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title, moderation_status, created_at, updated_at
                ) VALUES (
                    '0198f6c1-62ba-7d30-8b12-0f733b6570d4',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570a1',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570b2',
                    '0198f6c1-62ba-7d30-8b12-0f733b6570c3',
                    '오늘의 순간',
                    'APPROVED',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """);
    }

    @Test
    @DisplayName("공개 게시물에 좋아요를 등록하고 현재 상태를 반환한다")
    void likePost_visiblePost_returnsLikedResult() {
        // When
        PostLikeResult result = postLikeService.likePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(POST_ID, 1L, true));
        assertThat(countPostLikes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이미 좋아요한 게시물에 다시 등록해도 같은 상태를 반환한다")
    void likePost_existingPostLike_returnsSameResult() {
        // Given
        postLikeService.likePost(POST_ID, USER_ID);

        // When
        PostLikeResult result = postLikeService.likePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(POST_ID, 1L, true));
        assertThat(countPostLikes()).isEqualTo(1L);
    }

    @Test
    @DisplayName("등록된 좋아요를 취소하고 현재 상태를 반환한다")
    void unlikePost_existingPostLike_returnsUnlikedResult() {
        // Given
        postLikeService.likePost(POST_ID, USER_ID);

        // When
        PostLikeResult result = postLikeService.unlikePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(POST_ID, 0L, false));
        assertThat(countPostLikes()).isZero();
    }

    @Test
    @DisplayName("좋아요가 없는 게시물을 다시 취소해도 같은 상태를 반환한다")
    void unlikePost_missingPostLike_returnsSameResult() {
        // When
        PostLikeResult result = postLikeService.unlikePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(POST_ID, 0L, false));
        assertThat(countPostLikes()).isZero();
    }

    @Test
    @DisplayName("공개 가능한 게시물이 아니면 찾을 수 없음 예외를 발생시킨다")
    void likePost_invisiblePost_throwsNotFoundException() {
        // Given
        jdbcTemplate.update(
                "UPDATE posts SET moderation_status = 'REJECTED' WHERE id = ?",
                POST_ID
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postLikeService.likePost(POST_ID, USER_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
        assertThat(countPostLikes()).isZero();
    }

    @Test
    @DisplayName("인증된 사용자를 찾을 수 없으면 인증 예외를 발생시킨다")
    void likePost_unknownUser_throwsUnauthorizedException() {
        // When
        UnauthorizedException exception = catchThrowableOfType(
                UnauthorizedException.class,
                () -> postLikeService.likePost(POST_ID, UNKNOWN_USER_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(exception).hasMessage("유효하지 않은 인증 정보입니다.");
        assertThat(countPostLikes()).isZero();
    }

    private long countPostLikes() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Long.class,
                POST_ID
        );
        return count == null ? 0L : count;
    }
}
