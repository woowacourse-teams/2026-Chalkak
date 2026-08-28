package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chalkak.backend.exception.BaseException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostProcessingImageUpload;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** 관리자 검수·삭제와 이미지 처리 URL 발급의 잠금 경합을 실제 PostgreSQL에서 검증한다. */
class AdminPostModerationDeletionConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "SUCCESS";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final UUID MODERATOR_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575f1");
    private static final UUID DELETING_ADMIN_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575f2");
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575b1");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575c1");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575e1");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6575d1");
    private static final String STAGING_STORAGE_KEY =
            "chalkak/staging/test/posts/" + UPLOAD_ID + ".webp";
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";

    @Autowired
    private AdminPostModerationService adminPostModerationService;

    @Autowired
    private AdminPostDeletionService adminPostDeletionService;

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @MockitoBean
    private PostImageUploadIssuer postImageUploadIssuer;

    @BeforeEach
    void setUp() {
        cleanUp();
        insertAdmin(MODERATOR_ADMIN_ID, "moderation-race-moderator");
        insertAdmin(DELETING_ADMIN_ID, "moderation-race-deleter");
        insertUser();
        insertTopic();
        insertPhoto();
        insertUpload();
        insertPendingPost();
        given(postImageStorage.toStagingStorageKey(UPLOAD_ID))
                .willReturn(STAGING_STORAGE_KEY);
        given(postImageUploadIssuer.processingUploadUrlValidity())
                .willReturn(Duration.ofMinutes(5));
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    @DisplayName("같은 대기 게시물의 승인과 삭제가 겹쳐도 교착 없이 삭제 상태로 수렴한다")
    void moderateAndDelete_concurrentSamePendingPost_deletesConsistently()
            throws Exception {
        // Given
        Callable<OperationAttempt> moderate = () -> {
            AdminPostModerationResult result = adminPostModerationService.moderate(
                    POST_ID,
                    MODERATOR_ADMIN_ID,
                    ModerationStatus.APPROVED,
                    null
            );
            return new OperationAttempt(SUCCESS, result);
        };
        Callable<OperationAttempt> delete = () -> {
            adminPostDeletionService.deletePost(
                    POST_ID,
                    DELETING_ADMIN_ID,
                    "운영 정책 위반"
            );
            return new OperationAttempt(SUCCESS, null);
        };

        // When
        ConcurrentResult concurrentResult = runConcurrently(moderate, delete);

        // Then
        assertThat(concurrentResult.deletion().outcome()).isEqualTo(SUCCESS);
        assertThat(concurrentResult.moderation().outcome()).isIn(SUCCESS, NOT_FOUND);
        PostState post = findPostState();
        assertThat(post.postDeletedAt()).isNotNull();
        assertThat(post.photoDeletedAt()).isEqualTo(post.postDeletedAt());
        assertThat(countDeletionPlans()).isEqualTo(1);
        assertThat(countAudits("POST_DELETED")).isEqualTo(1);
        assertThat(findDeletionAuditBeforeStatus()).isEqualTo(post.moderationStatus());
        then(postImageStorage).should(never()).deleteImage(anyString());

        if (concurrentResult.moderation().outcome().equals(SUCCESS)) {
            assertSuccessfulModerationBeforeDeletion(concurrentResult, post);
            return;
        }
        assertDeletionBeforeModeration(post);
    }

    @Test
    @DisplayName("처리 URL 발급 중 삭제가 겹치면 발급 완료 뒤 삭제하고 URL 만료 후 정리를 예약한다")
    void issueProcessingUploadAndDelete_concurrentSamePost_serializesWithWriterLease()
            throws Exception {
        // Given
        PostProcessingImageUpload processingUpload = new PostProcessingImageUpload(
                "https://s3.test/original",
                "https://s3.test/thumbnail",
                "image/webp",
                "public, max-age=86400"
        );
        CountDownLatch issuerEntered = new CountDownLatch(1);
        CountDownLatch releaseIssuer = new CountDownLatch(1);
        given(postImageUploadIssuer.issueProcessingUpload(UPLOAD_ID))
                .willAnswer(invocation -> {
                    issuerEntered.countDown();
                    if (!releaseIssuer.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("처리 URL 발급 대기 시간 초과");
                    }
                    return processingUpload;
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<PostProcessingImageUpload> issuanceFuture = executor.submit(
                () -> postCommandService.issuePostImageProcessingUpload(UPLOAD_ID)
        );
        Future<Void> deletionFuture = null;

        try {
            assertThat(issuerEntered.await(5, TimeUnit.SECONDS)).isTrue();
            CountDownLatch deletionStarted = new CountDownLatch(1);
            deletionFuture = executor.submit(() -> {
                deletionStarted.countDown();
                adminPostDeletionService.deletePost(
                        POST_ID,
                        DELETING_ADMIN_ID,
                        "운영 정책 위반"
                );
                return null;
            });
            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<Void> blockedDeletion = deletionFuture;
            assertThatThrownBy(
                    () -> blockedDeletion.get(300, TimeUnit.MILLISECONDS)
            ).isInstanceOf(TimeoutException.class);

            releaseIssuer.countDown();

            assertThat(issuanceFuture.get(10, TimeUnit.SECONDS))
                    .isEqualTo(processingUpload);
            deletionFuture.get(10, TimeUnit.SECONDS);
        } finally {
            releaseIssuer.countDown();
            issuanceFuture.cancel(true);
            if (deletionFuture != null) {
                deletionFuture.cancel(true);
            }
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }

        // Then
        PostState post = findPostState();
        assertThat(post.postDeletedAt()).isNotNull();
        assertThat(Duration.between(
                post.postDeletedAt(),
                findDeletionPlanNextAttemptAt()
        )).isEqualTo(Duration.ofMinutes(6));
        assertThat(countDeletionPlans()).isEqualTo(1);
        assertThat(countAudits("POST_DELETED")).isEqualTo(1);
        then(postImageUploadIssuer).should().issueProcessingUpload(UPLOAD_ID);
    }

    private void assertSuccessfulModerationBeforeDeletion(
            ConcurrentResult concurrentResult,
            PostState post
    ) {
        AdminPostModerationResult moderationResult =
                concurrentResult.moderation().moderationResult();
        assertThat(moderationResult).isNotNull();
        assertThat(post.moderationStatus()).isEqualTo("APPROVED");
        assertThat(post.moderatedAt()).isNotNull();
        assertThat(post.moderatedAt()).isBeforeOrEqualTo(post.postDeletedAt());
        assertThat(countAudits("POST_APPROVED")).isEqualTo(1);
    }

    private void assertDeletionBeforeModeration(PostState post) {
        assertThat(post.moderationStatus()).isEqualTo("PENDING");
        assertThat(post.moderatedAt()).isNull();
        assertThat(countAudits("POST_APPROVED")).isZero();
    }

    private ConcurrentResult runConcurrently(
            Callable<OperationAttempt> moderation,
            Callable<OperationAttempt> deletion
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger threadNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "admin-post-moderation-deletion-" + threadNumber.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        });
        Future<OperationAttempt> moderationFuture = executor.submit(
                () -> attempt(barrier, moderation)
        );
        Future<OperationAttempt> deletionFuture = executor.submit(
                () -> attempt(barrier, deletion)
        );
        try {
            return new ConcurrentResult(
                    moderationFuture.get(10, TimeUnit.SECONDS),
                    deletionFuture.get(10, TimeUnit.SECONDS)
            );
        } finally {
            moderationFuture.cancel(true);
            deletionFuture.cancel(true);
            executor.shutdownNow();
            executor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private OperationAttempt attempt(
            CyclicBarrier barrier,
            Callable<OperationAttempt> action
    ) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
            return action.call();
        } catch (NotFoundException exception) {
            return new OperationAttempt(NOT_FOUND, null);
        } catch (BaseException exception) {
            return new OperationAttempt(exception.getErrorCode().name(), null);
        } catch (Exception exception) {
            return new OperationAttempt(
                    "UNEXPECTED:" + exception.getClass().getSimpleName(),
                    null
            );
        }
    }

    private PostState findPostState() {
        return jdbcTemplate.queryForObject("""
                SELECT CAST(post.moderation_status AS TEXT), post.moderated_at,
                       post.deleted_at, photo.deleted_at
                FROM posts post
                JOIN photos photo ON photo.id = post.photo_id
                WHERE post.id = ?
                """, (resultSet, rowNumber) -> new PostState(
                resultSet.getString(1),
                instant(resultSet.getTimestamp(2)),
                instant(resultSet.getTimestamp(3)),
                instant(resultSet.getTimestamp(4))
        ), POST_ID);
    }

    private int countDeletionPlans() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_media_deletion_plans WHERE post_id = ?",
                Integer.class,
                POST_ID
        );
    }

    private Instant findDeletionPlanNextAttemptAt() {
        return jdbcTemplate.queryForObject(
                "SELECT next_attempt_at FROM post_media_deletion_plans WHERE post_id = ?",
                Instant.class,
                POST_ID
        );
    }

    private int countAudits(String action) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM admin_audit_logs
                WHERE target_id = ?
                  AND action = CAST(? AS admin_action)
                """, Integer.class, POST_ID, action);
    }

    private String findDeletionAuditBeforeStatus() {
        return jdbcTemplate.queryForObject("""
                SELECT before_state ->> 'moderationStatus'
                FROM admin_audit_logs
                WHERE target_id = ?
                  AND action = CAST('POST_DELETED' AS admin_action)
                """, String.class, POST_ID);
    }

    private void cleanUp() {
        jdbcTemplate.update(
                "DELETE FROM post_media_deletion_plans WHERE post_id = ?",
                POST_ID
        );
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE target_id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE id = ?", POST_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE id = ?", PHOTO_ID);
        jdbcTemplate.update("DELETE FROM post_image_uploads WHERE id = ?", UPLOAD_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
        jdbcTemplate.update(
                "DELETE FROM admins WHERE id IN (?, ?)",
                MODERATOR_ADMIN_ID,
                DELETING_ADMIN_ID
        );
    }

    private void insertAdmin(UUID adminId, String username) {
        jdbcTemplate.update("""
                INSERT INTO admins (
                    id, username, password, created_at, updated_at
                ) VALUES (
                    ?, ?, 'test-password', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, adminId, username);
    }

    private void insertUser() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at
                ) VALUES (
                    ?, 'moderation-deletion-race@example.com',
                    CAST('ACTIVE' AS user_status),
                    'chalkak/signatures/moderation-deletion-race/original.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
    }

    private void insertTopic() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at,
                    created_at, updated_at
                ) VALUES (
                    ?, '검수 삭제 경합', CURRENT_DATE,
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
                """, UPLOAD_ID, USER_ID);
    }

    private void insertPendingPost() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status, moderated_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, '삭제 경합', CAST('PENDING' AS moderation_status), NULL,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, POST_ID, USER_ID, TOPIC_ID, PHOTO_ID, UPLOAD_ID);
    }

    private Instant instant(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toInstant();
    }

    private record OperationAttempt(
            String outcome,
            AdminPostModerationResult moderationResult
    ) {
    }

    private record ConcurrentResult(
            OperationAttempt moderation,
            OperationAttempt deletion
    ) {
    }

    private record PostState(
            String moderationStatus,
            Instant moderatedAt,
            Instant postDeletedAt,
            Instant photoDeletedAt
    ) {
    }
}
