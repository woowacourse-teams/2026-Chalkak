package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.chalkak.backend.admin.service.AdminPostModerationService;
import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.infrastructure.persistence.PostRepositoryImpl;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.Map;
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

/** 게시물 제목 수정과 관리자 검수가 같은 PostgreSQL 행 잠금으로 직렬화되는지 검증한다. */
class PostUpdateConcurrencyTest extends IntegrationTestSupport {

    private static final String UPDATE_THREAD = "post-title-update";
    private static final String SECOND_UPDATE_THREAD = "post-title-second-update";
    private static final String MODERATION_THREAD = "post-title-moderation";
    private static final String DELETION_THREAD = "post-title-deletion";
    private static final UUID ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6577f1");
    private static final UUID AUTHOR_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6577a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6577b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6577c1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6577d1");

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private AdminPostModerationService adminPostModerationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private PostRepositoryImpl postRepositorySpy;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin();
        insertAuthor();
        insertOpenTopic();
        insertPhoto();
        insertPendingPost();
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("제목 수정이 먼저 시작되면 검수는 수정 커밋을 기다린 뒤 승인한다")
    void updateAndModerate_updateLocksFirst_serializesAndApprovesUpdatedPost()
            throws Exception {
        // Given
        CountDownLatch updateLocked = new CountDownLatch(1);
        CountDownLatch moderationStarted = new CountDownLatch(1);
        CountDownLatch updateCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(MODERATION_THREAD)) {
                moderationStarted.countDown();
            }
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                updateLocked.countDown();
                await(updateCanCommit, "제목 수정 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(postRepositorySpy).findActiveByIdForUpdate(POST_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> update = executor.submit(() -> runNamed(UPDATE_THREAD, () -> {
            postCommandService.updatePost(AUTHOR_ID, POST_ID, "수정 제목");
            return null;
        }));
        await(updateLocked, "제목 수정이 게시물 락을 획득하지 못했습니다.");
        Future<Void> moderation = executor.submit(() -> runNamed(MODERATION_THREAD, () -> {
            adminPostModerationService.moderate(
                    POST_ID,
                    ADMIN_ID,
                    ModerationStatus.APPROVED,
                    null
            );
            return null;
        }));

        // When
        try {
            await(moderationStarted, "검수 요청이 게시물 락 조회를 시작하지 못했습니다.");
            assertThat(moderation.isDone()).isFalse();
        } finally {
            updateCanCommit.countDown();
        }
        update.get(5, TimeUnit.SECONDS);
        moderation.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        // Then
        Map<String, Object> post = findPostState();
        assertThat(post.get("title")).isEqualTo("수정 제목");
        assertThat(post.get("moderation_status").toString()).isEqualTo("APPROVED");
        assertThat(post.get("moderated_at")).isNotNull();
    }

    @Test
    @DisplayName("거절 검수가 먼저 시작되면 제목 수정은 검수 커밋을 기다린 뒤 거부된다")
    void updateAndModerate_rejectionLocksFirst_rejectsUpdateWithoutChangingTitle()
            throws Exception {
        // Given
        CountDownLatch moderationLocked = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch moderationCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                updateStarted.countDown();
            }
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(MODERATION_THREAD)) {
                moderationLocked.countDown();
                await(moderationCanCommit, "검수 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(postRepositorySpy).findActiveByIdForUpdate(POST_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> moderation = executor.submit(() -> runNamed(MODERATION_THREAD, () -> {
            adminPostModerationService.moderate(
                    POST_ID,
                    ADMIN_ID,
                    ModerationStatus.REJECTED,
                    "운영 정책 위반"
            );
            return null;
        }));
        await(moderationLocked, "검수 요청이 게시물 락을 획득하지 못했습니다.");
        Future<String> update = executor.submit(() -> runNamed(UPDATE_THREAD, () -> {
            try {
                postCommandService.updatePost(AUTHOR_ID, POST_ID, "수정 제목");
                return "SUCCESS";
            } catch (BaseException exception) {
                return exception.getErrorCode().name();
            }
        }));

        // When
        try {
            await(updateStarted, "제목 수정이 게시물 락 조회를 시작하지 못했습니다.");
            assertThat(update.isDone()).isFalse();
        } finally {
            moderationCanCommit.countDown();
        }
        moderation.get(5, TimeUnit.SECONDS);
        String updateOutcome = update.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        // Then
        assertThat(updateOutcome).isEqualTo("BUSINESS_ERROR");
        Map<String, Object> post = findPostState();
        assertThat(post.get("title")).isEqualTo("기존 제목");
        assertThat(post.get("moderation_status").toString()).isEqualTo("REJECTED");
        assertThat(post.get("moderated_at")).isNotNull();
    }

    @Test
    @DisplayName("삭제가 먼저 시작되면 제목 수정은 삭제 커밋을 기다린 뒤 거부된다")
    void updateAndDelete_deletionLocksFirst_rejectsUpdateWithoutChangingTitle()
            throws Exception {
        // Given
        CountDownLatch deletionLocked = new CountDownLatch(1);
        CountDownLatch updateStarted = new CountDownLatch(1);
        CountDownLatch deletionCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(DELETION_THREAD)) {
                deletionLocked.countDown();
                await(deletionCanCommit, "삭제 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(postRepositorySpy).findByIdForUpdate(POST_ID);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                updateStarted.countDown();
            }
            return invocation.callRealMethod();
        }).when(postRepositorySpy).findActiveByIdForUpdate(POST_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> deletion = executor.submit(() -> runNamed(DELETION_THREAD, () -> {
            postCommandService.deletePost(AUTHOR_ID, POST_ID);
            return null;
        }));
        await(deletionLocked, "삭제 요청이 게시물 락을 획득하지 못했습니다.");
        Future<String> update = executor.submit(() -> runNamed(UPDATE_THREAD, () -> {
            try {
                postCommandService.updatePost(AUTHOR_ID, POST_ID, "수정 제목");
                return "SUCCESS";
            } catch (BaseException exception) {
                return exception.getMessage();
            }
        }));

        // When
        try {
            await(updateStarted, "제목 수정이 게시물 락 조회를 시작하지 못했습니다.");
            assertThat(update.isDone()).isFalse();
        } finally {
            deletionCanCommit.countDown();
        }
        deletion.get(5, TimeUnit.SECONDS);
        String updateOutcome = update.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        // Then
        assertThat(updateOutcome).isEqualTo("게시물을 찾을 수 없습니다.");
        Map<String, Object> post = findPostState();
        assertThat(post.get("title")).isEqualTo("기존 제목");
        assertThat(post.get("deleted_at")).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 제목 수정은 게시물 락 순서대로 처리되어 마지막 제목을 저장한다")
    void updateAndUpdate_firstUpdateLocksFirst_appliesSecondTitleLast()
            throws Exception {
        // Given
        CountDownLatch firstUpdateLocked = new CountDownLatch(1);
        CountDownLatch secondUpdateStarted = new CountDownLatch(1);
        CountDownLatch firstUpdateCanCommit = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (Thread.currentThread().getName().equals(SECOND_UPDATE_THREAD)) {
                secondUpdateStarted.countDown();
            }
            Object result = invocation.callRealMethod();
            if (Thread.currentThread().getName().equals(UPDATE_THREAD)) {
                firstUpdateLocked.countDown();
                await(firstUpdateCanCommit, "첫 번째 제목 수정 커밋이 허용되지 않았습니다.");
            }
            return result;
        }).when(postRepositorySpy).findActiveByIdForUpdate(POST_ID);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<Void> firstUpdate = executor.submit(() -> runNamed(UPDATE_THREAD, () -> {
            postCommandService.updatePost(AUTHOR_ID, POST_ID, "첫 번째 제목");
            return null;
        }));
        await(firstUpdateLocked, "첫 번째 제목 수정이 게시물 락을 획득하지 못했습니다.");
        Future<Void> secondUpdate = executor.submit(() -> runNamed(SECOND_UPDATE_THREAD, () -> {
            postCommandService.updatePost(AUTHOR_ID, POST_ID, "두 번째 제목");
            return null;
        }));

        // When
        try {
            await(secondUpdateStarted, "두 번째 제목 수정이 게시물 락 조회를 시작하지 못했습니다.");
            assertThat(secondUpdate.isDone()).isFalse();
        } finally {
            firstUpdateCanCommit.countDown();
        }
        firstUpdate.get(5, TimeUnit.SECONDS);
        secondUpdate.get(5, TimeUnit.SECONDS);
        shutdown(executor);

        // Then
        Map<String, Object> post = findPostState();
        assertThat(post.get("title")).isEqualTo("두 번째 제목");
        assertThat(post.get("moderation_status").toString()).isEqualTo("PENDING");
    }

    private <T> T runNamed(String threadName, Task<T> task) throws Exception {
        Thread.currentThread().setName(threadName);
        return task.run();
    }

    private void await(CountDownLatch latch, String message) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(message);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트 대기가 중단됐습니다.", exception);
        }
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
    }

    private Map<String, Object> findPostState() {
        return jdbcTemplate.queryForMap("""
                SELECT title, moderation_status, moderated_at, deleted_at
                FROM posts
                WHERE id = ?
                """, POST_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", AUTHOR_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }

    private void insertAdmin() {
        jdbcTemplate.update("""
                INSERT INTO admins (id, username, password, created_at, updated_at)
                VALUES (?, 'post-update-moderator', 'test-password',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ADMIN_ID);
    }

    private void insertAuthor() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'post-update-concurrency@example.com',
                    CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/post-update-concurrency/original.webp',
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
                    ?, '수정 검수 경합', CURRENT_DATE,
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
                    ?, 'chalkak/posts/update-concurrency/original.webp',
                    'chalkak/posts/update-concurrency/thumbnail.webp', CAST('{}' AS jsonb),
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
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, AUTHOR_ID, TOPIC_ID, PHOTO_ID);
    }

    @FunctionalInterface
    private interface Task<T> {

        T run() throws Exception;
    }
}
