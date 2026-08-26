package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 업로드 행의 비관적 락이 실제로 경합하는지 확인한다. 다른 통합 테스트는 모두 단일 스레드 트랜잭션 안에서
 * 돌기 때문에 이 잠금이 한 번도 실제로 겨루지 않는다.
 */
class PostImageUploadConcurrencyTest extends IntegrationTestSupport {

    private static final String SUCCESS = "success";
    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e2");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570e3");
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";

    @Autowired
    private PostCreationService postCreationService;

    @Autowired
    private PostImageProcessingService postImageProcessingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-concurrency@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/signature.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '동시성 확인용 주제', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST('ISSUED' AS post_image_upload_status),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UPLOAD_ID, USER_ID);

        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn(THUMBNAIL_STORAGE_KEY);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM posts WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE original_storage_key = ?",
                ORIGINAL_STORAGE_KEY);
        jdbcTemplate.update("DELETE FROM post_image_uploads WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    @DisplayName("같은 업로드로 동시에 게시물을 만들면 하나만 성공한다")
    void createPost_concurrentSameUpload_createsSinglePost() throws Exception {
        // Given
        Callable<Void> createPost = () -> {
            postCreationService.createPost(USER_ID, TOPIC_ID, UPLOAD_ID, null);
            return null;
        };

        // When
        List<String> results = runConcurrently(createPost, createPost);

        // Then
        assertThat(countPosts()).isEqualTo(1);
        assertThat(countPhotos()).isEqualTo(1);
        // 잠금이 두 요청을 직렬화하면 뒤늦은 쪽은 이미 소비된 claim을 보고 거절된다. 잠금이 없으면 둘 다
        // 미소비 상태를 읽고 진행해 유니크 제약 위반으로 끝나므로, 실패 사유가 잠금 동작을 가른다.
        assertThat(results).containsExactlyInAnyOrder(SUCCESS, "이미 사용된 사진입니다.");
    }

    @Test
    @DisplayName("게시물 생성과 처리 완료 콜백이 겹쳐도 업로드가 한 번만 소비된다")
    void createPost_concurrentWithCompleteCallback_keepsStateConsistent() throws Exception {
        // Given
        Callable<Void> createPost = () -> {
            postCreationService.createPost(USER_ID, TOPIC_ID, UPLOAD_ID, null);
            return null;
        };
        Callable<Void> completeCallback = () -> {
            postImageProcessingService.completePostImageProcessing(
                    UPLOAD_ID,
                    Map.of("width", 4032, "height", 3024)
            );
            return null;
        };

        // When
        List<String> results = runConcurrently(createPost, completeCallback);

        // Then
        assertThat(results).contains(SUCCESS);
        assertThat(countPosts()).isEqualTo(1);
        assertThat(claimedCount()).isEqualTo(1);
        // 어느 순서로 끝나든 게시물은 검수 중이거나 공개된 상태로 수렴하고, 되돌아가지 않는다.
        assertThat(moderationStatus()).isIn("VALIDATING", "APPROVED");
    }

    private List<String> runConcurrently(Callable<Void> first, Callable<Void> second)
            throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> firstResult = executor.submit(() -> attempt(barrier, first));
            Future<String> secondResult = executor.submit(() -> attempt(barrier, second));

            return List.of(firstResult.get(), secondResult.get());
        }
    }

    private String attempt(CyclicBarrier barrier, Callable<Void> action) {
        try {
            barrier.await();
            action.call();
            return SUCCESS;
        } catch (Exception exception) {
            return exception.getMessage();
        }
    }

    private int countPosts() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE user_id = ?", Integer.class, USER_ID);
    }

    private int countPhotos() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM photos WHERE original_storage_key = ?",
                Integer.class,
                ORIGINAL_STORAGE_KEY
        );
    }

    private int claimedCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_image_uploads WHERE id = ? AND claimed_at IS NOT NULL",
                Integer.class,
                UPLOAD_ID
        );
    }

    private String moderationStatus() {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(moderation_status AS TEXT) FROM posts WHERE user_id = ?",
                String.class,
                USER_ID
        );
    }
}
