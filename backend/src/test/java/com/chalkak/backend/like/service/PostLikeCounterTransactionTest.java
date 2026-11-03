package com.chalkak.backend.like.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.admin.service.post.AdminPostCommandService;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.service.PostCommandService;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class PostLikeCounterTransactionTest extends IntegrationTestSupport {

    private static final UUID AUTHOR_ID = UUID.randomUUID();
    private static final UUID TOPIC_ID = UUID.randomUUID();
    private static final UUID PHOTO_ID = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID UPLOAD_ID = UUID.randomUUID();

    @Autowired
    private PostLikeService postLikeService;

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private AdminPostCommandService adminPostCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PostImageStorage postImageStorage;

    private List<UUID> likerIds;

    @BeforeEach
    void setUp() {
        new DatabaseCleaner(jdbcTemplate).clean();
        insertUser(AUTHOR_ID);
        likerIds = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            UUID userId = UUID.randomUUID();
            insertUser(userId);
            likerIds.add(userId);
        }
        jdbcTemplate.update(
                """
                        INSERT INTO topics (id, title, topic_date, starts_at, ends_at, created_at, updated_at)
                        VALUES (?, '집계 트랜잭션', CURRENT_DATE, CURRENT_TIMESTAMP - INTERVAL '1 hour',
                                CURRENT_TIMESTAMP + INTERVAL '1 hour', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                TOPIC_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO photos (id, original_storage_key, created_at, updated_at)
                        VALUES (?, 'counter-transaction/original.webp', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                PHOTO_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO posts (id, user_id, topic_id, photo_id, title, moderation_status,
                                           moderated_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, '집계 검증', 'APPROVED', CURRENT_TIMESTAMP,
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                POST_ID,
                AUTHOR_ID,
                TOPIC_ID,
                PHOTO_ID
        );
        jdbcTemplate.update("INSERT INTO post_like_counts (post_id) VALUES (?)", POST_ID);
        jdbcTemplate.update(
                """
                        INSERT INTO admins (id, username, password, created_at, updated_at)
                        VALUES (?, 'counter-transaction-admin', 'unused', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        ON CONFLICT (id) DO NOTHING
                        """,
                ADMIN_ID);
    }

    @AfterEach
    void tearDown() {
        new DatabaseCleaner(jdbcTemplate).clean();
        jdbcTemplate.update("DELETE FROM admin_audit_logs WHERE actor_admin_id = ?", ADMIN_ID);
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", ADMIN_ID);
    }

    @Test
    @DisplayName("서로 다른 사용자가 동시에 등록·취소해도 집계 증감이 유실되지 않는다")
    void likeAndUnlike_concurrentDifferentUsers_preservesEveryChange() throws Exception {
        // When
        runTogether(likerIds, userId -> postLikeService.likePost(POST_ID, userId));

        // Then
        assertCounts(8);

        // When
        runTogether(likerIds, userId -> postLikeService.unlikePost(POST_ID, userId));

        // Then
        assertCounts(0);
    }

    @Test
    @DisplayName("같은 사용자의 동시 등록·취소는 관계와 집계를 한 번만 바꾼다")
    void likeAndUnlike_concurrentSameUser_changesCountOnce() throws Exception {
        // Given
        List<UUID> sameUser = java.util.Collections.nCopies(8, likerIds.getFirst());

        // When
        runTogether(sameUser, userId -> postLikeService.likePost(POST_ID, userId));

        // Then
        assertCounts(1);

        // When
        runTogether(sameUser, userId -> postLikeService.unlikePost(POST_ID, userId));

        // Then
        assertCounts(0);
    }

    @Test
    @DisplayName("등록과 취소가 서로 다른 관계에서 겹쳐도 실제 관계 수와 집계가 같다")
    void likeAndUnlike_concurrentOppositeChanges_keepsExactCount() throws Exception {
        // Given
        List<UUID> existingUsers = likerIds.subList(0, 4);
        for (UUID userId : existingUsers) {
            postLikeService.likePost(POST_ID, userId);
        }

        // When
        runTogether(likerIds, userId -> {
            if (existingUsers.contains(userId)) {
                postLikeService.unlikePost(POST_ID, userId);
                return;
            }
            postLikeService.likePost(POST_ID, userId);
        });

        // Then
        assertCounts(4);
        assertThat(jdbcTemplate.queryForList(
                "SELECT user_id FROM post_likes WHERE post_id = ?",
                UUID.class,
                POST_ID
        ))
                .containsExactlyInAnyOrderElementsOf(likerIds.subList(4, 8));
    }

    @Test
    @DisplayName("집계 행이 없으면 좋아요 등록 전체를 롤백한다")
    void likePost_missingCounter_rollsBackRelationInsert() {
        // Given
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", POST_ID);

        // When & Then
        assertThatThrownBy(() -> postLikeService.likePost(POST_ID, likerIds.getFirst()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(relationCount()).isZero();
    }

    @Test
    @DisplayName("집계 행이 없으면 좋아요 취소 전체를 롤백한다")
    void unlikePost_missingCounter_rollsBackRelationDelete() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", POST_ID);

        // When & Then
        assertThatThrownBy(() -> postLikeService.unlikePost(POST_ID, likerIds.getFirst()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(relationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("잘못된 집계를 음수로 만들려 하면 관계 삭제도 롤백한다")
    void unlikePost_zeroCounterWithRelation_rollsBackRelationDelete() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());
        jdbcTemplate.update("UPDATE post_like_counts SET like_count = 0 WHERE post_id = ?",
                POST_ID);

        // When & Then
        assertThatThrownBy(() -> postLikeService.unlikePost(POST_ID, likerIds.getFirst()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(relationCount()).isEqualTo(1);
        assertThat(storedCount()).isZero();
    }

    @Test
    @DisplayName("작성자 삭제와 재요청은 집계 행을 남기고 0으로 만든다")
    void deletePost_authorAndRepeat_preservesZeroCounter() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());

        // When
        postCommandService.deletePost(AUTHOR_ID, POST_ID);
        postCommandService.deletePost(AUTHOR_ID, POST_ID);

        // Then
        assertCounts(0);
        assertDeleted(true);
    }

    @Test
    @DisplayName("관리자 삭제와 재요청은 집계 행을 남기고 0으로 만든다")
    void deletePost_adminAndRepeat_preservesZeroCounter() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());

        // When
        adminPostCommandService.deletePost(
                POST_ID,
                ADMIN_ID,
                "검증 삭제"
        );
        adminPostCommandService.deletePost(
                POST_ID,
                ADMIN_ID,
                "검증 재요청"
        );

        // Then
        assertCounts(0);
        assertDeleted(true);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Long.class,
                POST_ID
        ))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("작성자 삭제에서 집계 초기화가 실패하면 게시물·사진·관계가 남는다")
    void deletePost_authorMissingCounter_rollsBackDeletion() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", POST_ID);

        // When & Then
        assertThatThrownBy(() -> postCommandService.deletePost(AUTHOR_ID, POST_ID))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertDeleted(false);
        assertThat(relationCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("관리자 삭제에서 집계 초기화가 실패하면 게시물·사진·관계가 남는다")
    void deletePost_adminMissingCounter_rollsBackDeletion() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());
        jdbcTemplate.update("DELETE FROM post_like_counts WHERE post_id = ?", POST_ID);

        // When & Then
        assertThatThrownBy(() -> adminPostCommandService.deletePost(
                POST_ID,
                ADMIN_ID,
                "검증 삭제"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertDeleted(false);
        assertThat(relationCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_audit_logs WHERE target_id = ?",
                Long.class,
                POST_ID
        ))
                .isZero();
    }

    @Test
    @DisplayName("집계 초기화 이후 감사 로그 저장이 실패하면 집계와 관계도 되돌아간다")
    void deletePost_auditFailure_rollsBackCounterAndRelation() {
        // Given
        postLikeService.likePost(POST_ID, likerIds.getFirst());
        UUID unknownAdminId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> adminPostCommandService.deletePost(
                POST_ID,
                unknownAdminId,
                "검증 삭제"
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertCounts(1);
        assertDeleted(false);
    }

    @Test
    @DisplayName("게시물 생성 중 집계 저장이 실패하면 게시물·사진·업로드 점유를 롤백한다")
    void createPost_counterInsertFailure_rollsBackPostAndPhotoAndClaim() {
        // Given
        UUID userId = likerIds.getFirst();
        jdbcTemplate.update(
                """
                        INSERT INTO post_image_uploads (id, user_id, status, expires_at, created_at, updated_at)
                        VALUES (?, ?, 'ISSUED', CURRENT_TIMESTAMP + INTERVAL '1 hour',
                                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                UPLOAD_ID,
                userId
        );
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID)).willReturn("counter-failure.webp");
        // 이 테스트 DB에서 새 집계 INSERT만 거절하게 한다. 기존 행은 검증하지 않는다.
        jdbcTemplate.execute("""
                ALTER TABLE post_like_counts ADD CONSTRAINT test_reject_new_zero_counter
                CHECK (like_count > 0) NOT VALID
                """);
        try {
            // When & Then
            assertThatThrownBy(() -> postCommandService.createPost(
                    userId,
                    TOPIC_ID,
                    UPLOAD_ID,
                    "집계 실패",
                    null
            ))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            jdbcTemplate.execute("""
                    ALTER TABLE post_like_counts DROP CONSTRAINT test_reject_new_zero_counter
                    """);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE user_id = ?",
                Long.class,
                userId
        )).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM photos WHERE original_storage_key = 'counter-failure.webp'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status::text FROM post_image_uploads WHERE id = ?",
                String.class,
                UPLOAD_ID
        ))
                .isEqualTo("ISSUED");
        assertCounts(0);
    }

    private void runTogether(List<UUID> users, Consumer<UUID> action) throws Exception {
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(users.size())) {
            List<Future<?>> futures = new ArrayList<>();
            for (UUID userId : users) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 요청 시작을 기다리지 못했습니다.");
                    }
                    action.accept(userId);
                    return null;
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            try {
                for (Future<?> future : futures) {
                    future.get(20, TimeUnit.SECONDS);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void insertUser(UUID userId) {
        jdbcTemplate.update(
                """
                        INSERT INTO users (id, email, status, signature_original_storage_key, created_at, updated_at)
                        VALUES (?, ?, 'ACTIVE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                        """,
                userId,
                userId + "@example.com",
                "counter-signature/" + userId + ".webp"
        );
    }

    private long relationCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_likes WHERE post_id = ?",
                Long.class,
                POST_ID
        );
    }

    private long storedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT like_count FROM post_like_counts WHERE post_id = ?",
                Long.class,
                POST_ID
        );
    }

    private void assertCounts(long expected) {
        assertThat(relationCount()).isEqualTo(expected);
        assertThat(storedCount()).isEqualTo(expected);
    }

    private void assertDeleted(boolean expected) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM posts WHERE id = ?",
                Boolean.class,
                POST_ID
        ))
                .isEqualTo(expected);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM photos WHERE id = ?",
                Boolean.class,
                PHOTO_ID
        ))
                .isEqualTo(expected);
    }
}
