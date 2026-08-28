package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.like.infrastructure.persistence.PostLikeRepositoryImpl;
import com.chalkak.backend.post.repository.PostRepository;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PostLikeDeletionConcurrencyTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575a1");
    private static final UUID LIKER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575a2");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575d1");

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    private PostLikeRepositoryImpl postLikeRepository;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertUsers();
        insertTopic();
        insertPhoto();
        insertApprovedPost();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("게시물 삭제가 시작된 뒤의 좋아요 등록은 삭제 완료 후 거부한다")
    void likePost_startedAfterDeletion_rejectsLikeAndLeavesNoLike() throws Exception {
        // Given
        CountDownLatch deletionApplied = new CountDownLatch(1);
        CountDownLatch likeReachedCreation = new CountDownLatch(1);
        doAnswer(invocation -> {
            likeReachedCreation.countDown();
            return invocation.callRealMethod();
        }).when(postLikeRepository).createIfAbsent(POST_ID, LIKER_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // When
        Future<Void> deletion = executor.submit(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                postCommandService.deletePost(AUTHOR_ID, POST_ID);
                deletionApplied.countDown();
                await(likeReachedCreation);
            });
            return null;
        });
        Future<String> like = executor.submit(() -> {
            if (!deletionApplied.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("게시물 삭제가 시작되지 않았습니다.");
            }
            try {
                postLikeService.likePost(POST_ID, LIKER_ID);
                return "success";
            } catch (NotFoundException exception) {
                return exception.getMessage();
            }
        });

        deletion.get(5, TimeUnit.SECONDS);
        String likeResult = like.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertThat(likeResult).isEqualTo("게시물을 찾을 수 없습니다.");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Integer.class,
                POST_ID
        )).isZero();
    }

    @Test
    @DisplayName("같은 게시물의 공유 락은 두 트랜잭션이 동시에 획득한다")
    void findVisibleByIdForShare_concurrentRequests_acquireLocksTogether() throws Exception {
        // Given
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch secondLockAcquired = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        // When
        Future<Boolean> first = executor.submit(() -> transactionTemplate.execute(status -> {
            assertThat(postRepository.findVisibleByIdForShare(POST_ID)).isPresent();
            firstLockAcquired.countDown();
            return await(secondLockAcquired, 3);
        }));
        Future<Void> second = executor.submit(() -> {
            if (!firstLockAcquired.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("첫 번째 공유 락이 획득되지 않았습니다.");
            }
            transactionTemplate.executeWithoutResult(status -> {
                assertThat(postRepository.findVisibleByIdForShare(POST_ID)).isPresent();
                secondLockAcquired.countDown();
            });
            return null;
        });

        boolean acquiredTogether = first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Then
        assertThat(acquiredTogether).isTrue();
    }

    private boolean await(CountDownLatch latch) {
        return await(latch, 1);
    }

    private boolean await(CountDownLatch latch, long timeoutSeconds) {
        try {
            return latch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }

    private void insertUsers() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES
                    (?, 'post-deletion-author@example.com', 'ACTIVE',
                     'chalkak/signatures/deletion-author.webp',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
                    (?, 'post-deletion-liker@example.com', 'ACTIVE',
                     'chalkak/signatures/deletion-liker.webp',
                     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, AUTHOR_ID, LIKER_ID);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '삭제와 좋아요 동시성', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
    }

    private void insertPhoto() {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/posts/test/original/deletion-concurrency.webp',
                    'chalkak/posts/test/thumbnail/deletion-concurrency.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID);
    }

    private void insertApprovedPost() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, title,
                    moderation_status, moderated_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, '동시성 삭제 대상',
                    'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, AUTHOR_ID, TOPIC_ID, PHOTO_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM post_likes WHERE post_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id IN (?, ?)", AUTHOR_ID, LIKER_ID);
    }
}
