package com.chalkak.backend.like.service;

import static com.chalkak.backend.like.support.PostLikeCountFixtures.synchronizeFixtureCounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
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
    private static final UUID UNKNOWN_USER_ID = UUID
            .fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a3");
    private static final UUID POST_ID = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");

    private int userSequence;

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

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
        jdbcTemplate
                .update("""
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
        synchronizeFixtureCounts(jdbcTemplate);
    }

    @Test
    @DisplayName("공개 게시물에 좋아요를 등록하고 현재 상태를 반환한다")
    void likePost_visiblePost_returnsLikedResult() {
        // When
        PostLikeResult result = postLikeService.likePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(
                POST_ID,
                1L,
                true
        ));
        assertThat(countPostLikes()).isEqualTo(1L);
        assertThat(countMilestones()).isZero();
    }

    @Test
    @DisplayName("이미 좋아요한 게시물에 다시 등록해도 같은 상태를 반환한다")
    void likePost_existingPostLike_returnsSameResult() {
        // Given
        postLikeService.likePost(POST_ID, USER_ID);

        // When
        PostLikeResult result = postLikeService.likePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(
                POST_ID,
                1L,
                true
        ));
        assertThat(countPostLikes()).isEqualTo(1L);
        assertThat(countMilestones()).isZero();
    }

    @Test
    @DisplayName("등록된 좋아요를 취소하고 현재 상태를 반환한다")
    void unlikePost_existingPostLike_returnsUnlikedResult() {
        // Given
        postLikeService.likePost(POST_ID, USER_ID);

        // When
        PostLikeResult result = postLikeService.unlikePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(
                POST_ID,
                0L,
                false
        ));
        assertThat(countPostLikes()).isZero();
        assertThat(countMilestones()).isZero();
    }

    @Test
    @DisplayName("좋아요가 없는 게시물을 다시 취소해도 같은 상태를 반환한다")
    void unlikePost_missingPostLike_returnsSameResult() {
        // When
        PostLikeResult result = postLikeService.unlikePost(POST_ID, USER_ID);

        // Then
        assertThat(result).isEqualTo(new PostLikeResult(
                POST_ID,
                0L,
                false
        ));
        assertThat(countPostLikes()).isZero();
        assertThat(countMilestones()).isZero();
    }

    @Test
    @DisplayName("새 좋아요가 5단계에 도달하면 작성자의 발행 대기 마일스톤을 생성한다")
    void likePost_reachesMilestone_createsPendingMilestoneForAuthor() {
        // Given
        insertLikes(4);
        UUID fifthLikerId = insertUser();

        // When
        PostLikeResult result = postLikeService.likePost(POST_ID, fifthLikerId);

        // Then
        assertThat(result.likeCount()).isEqualTo(5L);
        entityManager.flush();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT body FROM notifications WHERE source_id = ?",
                String.class,
                POST_ID
        )).isEqualTo("좋아요 5개 이상을 달성했어요");
        assertThat(lastMilestoneCount()).isEqualTo(5L);
        assertThat(findMilestones()).containsExactly(new MilestoneRow(
                AUTHOR_ID,
                "좋아요 5개 이상을 달성했어요",
                "PENDING"
        ));
    }

    @Test
    @DisplayName("작성자 자신의 좋아요로 5단계에 도달해도 마일스톤을 생성한다")
    void likePost_authorReachesMilestone_createsPendingMilestone() {
        // Given
        insertLikes(4);

        // When
        postLikeService.likePost(POST_ID, AUTHOR_ID);

        // Then
        assertThat(findMilestones()).containsExactly(new MilestoneRow(
                AUTHOR_ID,
                "좋아요 5개 이상을 달성했어요",
                "PENDING"
        ));
    }

    @Test
    @DisplayName("푸시 설정이 꺼져도 알림함에 저장하고 최고 단계를 갱신한다")
    void likePost_pushDisabled_createsInboxNotificationWithoutPublication() {
        // Given
        jdbcTemplate.update("UPDATE users SET like_push_enabled = FALSE WHERE id = ?", AUTHOR_ID);
        insertLikes(4);

        // When
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(findMilestones()).containsExactly(new MilestoneRow(
                AUTHOR_ID,
                "좋아요 5개 이상을 달성했어요",
                "NOT_REQUIRED"
        ));
        assertThat(lastMilestoneCount()).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT next_attempt_at IS NULL FROM notifications WHERE source_id = ?",
                Boolean.class,
                POST_ID
        )).isTrue();
    }

    @Test
    @DisplayName("설정을 다시 켜도 OFF에서 만든 단계는 소급 발송하지 않고 다음 단계만 생성한다")
    void likePost_enabledAfterRecordedMilestone_createsOnlyNextMilestone() {
        // Given
        jdbcTemplate.update("UPDATE users SET like_push_enabled = FALSE WHERE id = ?", AUTHOR_ID);
        insertLikes(4);
        postLikeService.likePost(POST_ID, insertUser());
        jdbcTemplate.update("UPDATE users SET like_push_enabled = TRUE WHERE id = ?", AUTHOR_ID);
        entityManager.clear();

        // When
        for (int index = 0; index < 4; index++) {
            postLikeService.likePost(POST_ID, insertUser());
        }

        // Then
        assertThat(countMilestones()).isOne();
        assertThat(lastMilestoneCount()).isEqualTo(5L);

        // When
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(findMilestones()).containsExactly(
                new MilestoneRow(
                        AUTHOR_ID,
                        "좋아요 5개 이상을 달성했어요",
                        "NOT_REQUIRED"
                ),
                new MilestoneRow(
                        AUTHOR_ID,
                        "좋아요 10개 이상을 달성했어요",
                        "PENDING"
                )
        );
        assertThat(lastMilestoneCount()).isEqualTo(10L);
    }

    @Test
    @DisplayName("기존 게시물의 최고 단계 초기화 후에는 다음 새 단계에서만 알림을 생성한다")
    void likePost_initializedExistingPost_createsOnlyNewMilestone() {
        // Given
        insertLikes(17);
        jdbcTemplate.update(
                "UPDATE post_like_counts SET last_milestone_count = 15 WHERE post_id = ?", POST_ID
        );

        // When
        postLikeService.likePost(POST_ID, insertUser());
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(countMilestones()).isZero();
        assertThat(lastMilestoneCount()).isEqualTo(15L);

        // When
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(findMilestones())
                .containsExactly(new MilestoneRow(
                        AUTHOR_ID,
                        "좋아요 20개 이상을 달성했어요",
                        "PENDING"
                ));
        assertThat(lastMilestoneCount()).isEqualTo(20L);
    }

    @Test
    @DisplayName("알림이 보관 기간 후 삭제돼도 같은 단계를 재생성하지 않는다")
    void likePost_notificationDeleted_keepsMilestoneProgress() {
        // Given
        insertLikes(4);
        UUID fifthLiker = insertUser();
        postLikeService.likePost(POST_ID, fifthLiker);
        entityManager.flush();
        jdbcTemplate.update("DELETE FROM notifications WHERE source_id = ?", POST_ID);
        postLikeService.unlikePost(POST_ID, fifthLiker);

        // When
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(countMilestones()).isZero();
        assertThat(lastMilestoneCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("이미 기록한 단계는 좋아요가 감소한 뒤 다시 도달해도 중복 생성하지 않는다")
    void likePost_reachesRecordedMilestoneAgain_doesNotCreateDuplicate() {
        // Given
        insertLikes(4);
        UUID fifthLikerId = insertUser();
        postLikeService.likePost(POST_ID, fifthLikerId);
        postLikeService.unlikePost(POST_ID, fifthLikerId);

        // When
        postLikeService.likePost(POST_ID, insertUser());

        // Then
        assertThat(countPostLikes()).isEqualTo(5L);
        assertThat(findMilestones()).containsExactly(new MilestoneRow(
                AUTHOR_ID,
                "좋아요 5개 이상을 달성했어요",
                "PENDING"
        ));
    }

    @Test
    @DisplayName("이미 등록된 좋아요 요청은 마일스톤을 추가하지 않는다")
    void likePost_duplicateLike_doesNotCreateAdditionalMilestone() {
        // Given
        insertLikes(4);
        UUID fifthLikerId = insertUser();
        postLikeService.likePost(POST_ID, fifthLikerId);

        // When
        postLikeService.likePost(POST_ID, fifthLikerId);

        // Then
        assertThat(countMilestones()).isOne();
    }

    @Test
    @DisplayName("좋아요를 취소해도 이미 달성한 마일스톤은 유지한다")
    void unlikePost_recordedMilestone_keepsMilestone() {
        // Given
        insertLikes(4);
        UUID fifthLikerId = insertUser();
        postLikeService.likePost(POST_ID, fifthLikerId);

        // When
        postLikeService.unlikePost(POST_ID, fifthLikerId);

        // Then
        assertThat(countPostLikes()).isEqualTo(4L);
        assertThat(countMilestones()).isOne();
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

    private void insertLikes(int count) {
        for (int index = 0; index < count; index++) {
            UUID userId = insertUser();
            jdbcTemplate.update(
                    "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                    POST_ID,
                    userId
            );
        }
        synchronizeFixtureCounts(jdbcTemplate);
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        userSequence++;
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, status, signature_original_storage_key, created_at, updated_at
                        ) VALUES (?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId,
                "post-like-extra-%d@example.com".formatted(userSequence),
                "chalkak/dev/signatures/post-like-extra-%d.png".formatted(userSequence)
        );
        return userId;
    }

    private long lastMilestoneCount() {
        return jdbcTemplate.queryForObject(
                "SELECT last_milestone_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        );
    }

    private long countMilestones() {
        entityManager.flush();
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE source_id = ? AND type = 'POST_LIKE_MILESTONE'",
                Long.class,
                POST_ID
        );
        return count == null ? 0L : count;
    }

    private List<MilestoneRow> findMilestones() {
        entityManager.flush();
        return jdbcTemplate.query(
                """
                        SELECT user_id, body, sqs_publish_status
                        FROM notifications
                        WHERE source_id = ? AND type = 'POST_LIKE_MILESTONE'
                        ORDER BY created_at, id
                        """,
                (resultSet, rowNumber) -> new MilestoneRow(
                        resultSet.getObject("user_id", UUID.class),
                        resultSet.getString("body"),
                        resultSet.getString("sqs_publish_status")
                ),
                POST_ID
        );
    }

    private record MilestoneRow(
            UUID userId,
            String body,
            String publishStatus
    ) {
    }
}
