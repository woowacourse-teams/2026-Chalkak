package com.chalkak.backend.like.service;

import static com.chalkak.backend.like.support.PostLikeCountFixtures.synchronizeFixtureCounts;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import com.chalkak.backend.like.infrastructure.persistence.PostLikeCounterRepositoryImpl;
import com.chalkak.backend.notification.domain.PostLikePushMilestone;
import com.chalkak.backend.notification.infrastructure.persistence.PostLikePushMilestoneRepositoryImpl;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class PostLikeMilestoneConcurrencyTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432c101");
    private static final UUID FIRST_LIKER_ID = UUID
            .fromString("019a36f0-3db8-72dc-8f2d-07a15432c102");
    private static final UUID SECOND_LIKER_ID = UUID
            .fromString("019a36f0-3db8-72dc-8f2d-07a15432c103");
    private static final UUID TOPIC_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432c104");
    private static final UUID PHOTO_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432c105");
    private static final UUID POST_ID = UUID.fromString("019a36f0-3db8-72dc-8f2d-07a15432c106");

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PostLikeCounterRepositoryImpl postLikeCounterRepositorySpy;

    @MockitoSpyBean
    private PostLikePushMilestoneRepositoryImpl postLikePushMilestoneRepositorySpy;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
        insertFixture();
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("동시 좋아요가 5단계를 통과해도 마일스톤을 한 건만 생성한다")
    void likePost_concurrentLikesReachMilestone_createsOneMilestone() throws Exception {
        // Given
        CountDownLatch firstIncremented = new CountDownLatch(1);
        CountDownLatch secondIncrementStarted = new CountDownLatch(1);
        CountDownLatch firstCanContinue = new CountDownLatch(1);
        AtomicInteger incrementCount = new AtomicInteger();
        doAnswer(invocation -> {
            int incrementOrder = incrementCount.incrementAndGet();
            if (incrementOrder == 1) {
                Object result = invocation.callRealMethod();
                firstIncremented.countDown();
                if (!firstCanContinue.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("첫 번째 좋아요 처리가 허용되지 않았습니다.");
                }
                return result;
            }
            secondIncrementStarted.countDown();
            return invocation.callRealMethod();
        }).when(postLikeCounterRepositorySpy).increment(POST_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // When
        Future<PostLikeResult> first = executor
                .submit(() -> postLikeService.likePost(POST_ID, FIRST_LIKER_ID));
        if (!firstIncremented.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("첫 번째 좋아요 집계가 증가하지 않았습니다.");
        }
        Future<PostLikeResult> second = executor
                .submit(() -> postLikeService.likePost(POST_ID, SECOND_LIKER_ID));

        try {
            if (!secondIncrementStarted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("두 번째 좋아요 집계 증가가 시작되지 않았습니다.");
            }
            assertThat(second.isDone()).isFalse();
        } finally {
            firstCanContinue.countDown();
        }

        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT like_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isEqualTo(5L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like_push_milestones WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                """
                        SELECT milestone_count
                        FROM post_like_push_milestones
                        WHERE post_id = ?
                        """,
                Long.class,
                POST_ID
        )).isEqualTo(5L);
    }

    @Test
    @DisplayName("마일스톤 저장이 실패하면 좋아요 관계와 집계 증가를 함께 롤백한다")
    void likePost_milestoneSaveFails_rollsBackLikeAndCount() {
        // Given
        insertSeedLike();
        doThrow(new DataIntegrityViolationException("마일스톤 저장 실패"))
                .when(postLikePushMilestoneRepositorySpy)
                .save(any(PostLikePushMilestone.class));

        // When & Then
        assertThatThrownBy(() -> postLikeService.likePost(POST_ID, FIRST_LIKER_ID))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("마일스톤 저장 실패");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT like_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isEqualTo(4L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_like_push_milestones WHERE post_id = ?",
                Long.class,
                POST_ID
        )).isZero();
    }

    private void insertFixture() {
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, status, signature_original_storage_key,
                            created_at, updated_at
                        ) VALUES
                            (?, 'milestone-author@example.com', 'ACTIVE',
                             'chalkak/signatures/milestone-author.webp',
                             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                            (?, 'milestone-first@example.com', 'ACTIVE',
                             'chalkak/signatures/milestone-first.webp',
                             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                            (?, 'milestone-second@example.com', 'ACTIVE',
                             'chalkak/signatures/milestone-second.webp',
                             CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                AUTHOR_ID,
                FIRST_LIKER_ID,
                SECOND_LIKER_ID
        );
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '좋아요 마일스톤 동시성', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/test/original/milestone-concurrency.webp',
                    'chalkak/posts/test/thumbnail/milestone-concurrency.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (
                            id, user_id, topic_id, photo_id, title,
                            moderation_status, moderated_at, created_at, updated_at
                        ) VALUES (
                            ?, ?, ?, ?, '동시성',
                            'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        """,
                POST_ID,
                AUTHOR_ID,
                TOPIC_ID,
                PHOTO_ID
        );
        insertSeedLikers();
        synchronizeFixtureCounts(jdbcTemplate);
    }

    private void insertSeedLikers() {
        for (int index = 0; index < 3; index++) {
            UUID userId = UUID.randomUUID();
            jdbcTemplate.update(
                    """
                            INSERT INTO users (
                                id, email, status, signature_original_storage_key,
                                created_at, updated_at
                            ) VALUES (?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """,
                    userId,
                    "milestone-seed-%d@example.com".formatted(index),
                    "chalkak/signatures/milestone-seed-%d.webp".formatted(index)
            );
            jdbcTemplate.update(
                    "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                    POST_ID,
                    userId
            );
        }
    }

    private void insertSeedLike() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                        INSERT INTO users (
                            id, email, status, signature_original_storage_key,
                            created_at, updated_at
                        ) VALUES (?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId,
                "milestone-seed-extra@example.com",
                "chalkak/signatures/milestone-seed-extra.webp"
        );
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                POST_ID,
                userId
        );
        synchronizeFixtureCounts(jdbcTemplate);
    }
}
