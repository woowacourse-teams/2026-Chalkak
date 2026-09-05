package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.DatabaseCleaner;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

class PostModerationNotificationTransactionTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000001");
    private static final UUID TOPIC_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000002");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0199a002-0000-7000-8000-000000000003");

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    private DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        databaseCleaner = new DatabaseCleaner(jdbcTemplate);
        databaseCleaner.clean();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'notification-transaction@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/notification-transaction.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '알림 트랜잭션 테스트', CURRENT_DATE + 101,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/original/" + UPLOAD_ID + ".webp");
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp");
        given(notificationSender.send(any())).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("READY 업로드로 만든 게시물이 커밋되면 관리자 알림을 한 번 보낸다")
    void createPost_readyUpload_sendsAfterCommit() {
        // Given
        insertUpload("READY", "{\"width\": 4032, \"height\": 3024}");

        // When
        PostCreationResult result = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "생성 알림"
        );

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT moderation_status::text FROM posts WHERE id = ?",
                String.class,
                result.postId()
        )).isEqualTo("PENDING");
        then(notificationSender).should().send(argThat(message ->
                message.actionUri().toString().endsWith("/posts/" + result.postId())
        ));
    }

    @Test
    @DisplayName("이미지 완료 콜백이 게시물을 PENDING으로 커밋하면 관리자 알림을 한 번 보낸다")
    void completePostImageProcessing_validatingPost_sendsAfterCommit() {
        // Given
        insertUpload("ISSUED", null);
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        UUID postId = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "콜백 알림"
        ).postId();

        // When
        postCommandService.completePostImageProcessing(
                UPLOAD_ID,
                Map.of("width", 4032, "height", 3024)
        );

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT moderation_status::text FROM posts WHERE id = ?",
                String.class,
                postId
        )).isEqualTo("PENDING");
        then(notificationSender).should().send(argThat(message ->
                message.actionUri().toString().endsWith("/posts/" + postId)
        ));
    }

    @Test
    @DisplayName("이미지 완료 콜백이 중복으로 와도 관리자 알림은 한 번만 보낸다")
    void completePostImageProcessing_duplicateCallback_sendsOnce() {
        // Given
        insertUpload("ISSUED", null);
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        UUID postId = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "중복 콜백 알림"
        ).postId();

        // When
        postCommandService.completePostImageProcessing(
                UPLOAD_ID,
                Map.of("width", 4032, "height", 3024)
        );
        postCommandService.completePostImageProcessing(
                UPLOAD_ID,
                Map.of("width", 1, "height", 1)
        );

        // Then
        then(notificationSender).should(times(1)).send(argThat(message ->
                message.actionUri().toString().endsWith("/posts/" + postId)
        ));
    }

    @Test
    @DisplayName("게시물 트랜잭션이 롤백되면 관리자 알림을 보내지 않는다")
    void createPost_rolledBack_doesNotSendNotification() {
        // Given
        insertUpload("READY", "{\"width\": 4032, \"height\": 3024}");

        // When
        transactionTemplate.executeWithoutResult(status -> {
            postCommandService.createPost(USER_ID, TOPIC_ID, UPLOAD_ID, "롤백 알림");
            status.setRollbackOnly();
        });

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM posts WHERE user_id = ? AND topic_id = ?",
                Integer.class,
                USER_ID,
                TOPIC_ID
        )).isZero();
        then(notificationSender).shouldHaveNoInteractions();
    }

    private void insertUpload(String status, String imageMetadata) {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, image_metadata, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_image_upload_status), CAST(? AS jsonb),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UPLOAD_ID, USER_ID, status, imageMetadata);
    }

}
