package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.notification.repository.NotificationOutboxRepository;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostImageStorage;
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

class PostNotificationOutboxTransactionTest extends IntegrationTestSupport {

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

    @MockitoBean
    private NotificationOutboxRepository notificationOutboxRepository;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @BeforeEach
    void setUp() {
        cleanTestRows();
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
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, image_metadata, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, 'READY', '{"width": 4032, "height": 3024}'::jsonb,
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, UPLOAD_ID, USER_ID);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/original/" + UPLOAD_ID + ".webp");
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp");
    }

    @AfterEach
    void tearDown() {
        cleanTestRows();
    }

    @Test
    @DisplayName("Outbox 적재가 실패하면 게시물의 PENDING 전환과 생성도 함께 롤백한다")
    void createPost_outboxInsertFailure_rollsBackPostAndUploadClaim() {
        // Given
        given(notificationOutboxRepository.createPostModerationPending(any(), any()))
                .willThrow(new IllegalStateException("합성 Outbox 저장 실패"));

        // When & Then
        assertThatThrownBy(() -> postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "롤백 검증"
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("합성 Outbox 저장 실패");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM posts WHERE user_id = ? AND topic_id = ?
                """, Integer.class, USER_ID, TOPIC_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT claimed_at IS NULL FROM post_image_uploads WHERE id = ?
                """, Boolean.class, UPLOAD_ID)).isTrue();
    }

    @Test
    @DisplayName("완료 콜백의 Outbox 적재가 실패하면 READY와 PENDING 전환을 함께 롤백한다")
    void completePostImageProcessing_outboxInsertFailure_rollsBackStateChanges() {
        // Given
        jdbcTemplate.update("""
                UPDATE post_image_uploads
                SET status = 'ISSUED', image_metadata = NULL
                WHERE id = ?
                """, UPLOAD_ID);
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        PostCreationResult created = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "콜백 롤백"
        );
        given(notificationOutboxRepository.createPostModerationPending(any(), any()))
                .willThrow(new IllegalStateException("합성 Outbox 저장 실패"));

        // When & Then
        assertThatThrownBy(() -> postCommandService.completePostImageProcessing(
                UPLOAD_ID,
                Map.of("width", 4032, "height", 3024)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("합성 Outbox 저장 실패");

        assertThat(jdbcTemplate.queryForObject("""
                SELECT moderation_status::text FROM posts WHERE id = ?
                """, String.class, created.postId())).isEqualTo("VALIDATING");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT status::text FROM post_image_uploads WHERE id = ?
                """, String.class, UPLOAD_ID)).isEqualTo("ISSUED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT thumbnail_storage_key IS NULL
                FROM photos
                WHERE id = (SELECT photo_id FROM posts WHERE id = ?)
                """, Boolean.class, created.postId())).isTrue();
    }

    private void cleanTestRows() {
        jdbcTemplate.update("DELETE FROM notification_outboxes WHERE post_id IN "
                + "(SELECT id FROM posts WHERE user_id = ?)", USER_ID);
        jdbcTemplate.update("DELETE FROM posts WHERE user_id = ?", USER_ID);
        jdbcTemplate.update("DELETE FROM photos WHERE original_storage_key LIKE ?",
                "%" + UPLOAD_ID + "%");
        jdbcTemplate.update("DELETE FROM post_image_uploads WHERE id = ?", UPLOAD_ID);
        jdbcTemplate.update("DELETE FROM topics WHERE id = ?", TOPIC_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }
}
