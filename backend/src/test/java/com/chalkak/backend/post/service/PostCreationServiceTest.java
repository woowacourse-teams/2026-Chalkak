package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PostCreationServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a1");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b2");
    private static final UUID PHOTO_UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c3");
    private static final UUID SECOND_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a2");
    private static final UUID SECOND_TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b3");
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + PHOTO_UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + PHOTO_UPLOAD_ID + ".webp";

    @Autowired
    private PostCommandService postCommandService;

    @Autowired
    private PostQueryService postQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @MockitoBean
    private RandomSeedGenerator randomSeedGenerator;

    @MockitoBean
    private PostImageStorage postImageStorage;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-create@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/signature.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '지금 가장 기억에 남는 순간', CURRENT_DATE,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID);
        insertUpload(PHOTO_UPLOAD_ID, USER_ID, "ISSUED");
    }

    private void insertUpload(UUID uploadId, UUID userId, String status) {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_image_upload_status),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, uploadId, userId, status);
    }

    @Test
    @DisplayName("활성 사용자가 열린 주제에 업로드한 사진으로 게시물을 생성한다")
    void createPost_validRequest_savesPhotoAndValidatingPost() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);

        // When
        PostCreationResult result = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                "오늘의 기록"
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.postId()).isNotNull();
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.VALIDATING);

        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT p.user_id, p.topic_id, p.title, p.moderation_status,
                       ph.original_storage_key, ph.thumbnail_storage_key, ph.metadata
                FROM posts p
                JOIN photos ph ON ph.id = p.photo_id
                WHERE p.id = ?
                """, result.postId());
        assertThat(saved.get("user_id")).isEqualTo(USER_ID);
        assertThat(saved.get("topic_id")).isEqualTo(TOPIC_ID);
        assertThat(saved.get("title")).isEqualTo("오늘의 기록");
        assertThat(saved.get("moderation_status").toString()).isEqualTo("VALIDATING");
        assertThat(saved.get("original_storage_key")).isEqualTo(ORIGINAL_STORAGE_KEY);
        assertThat(saved.get("thumbnail_storage_key")).isNull();
        assertThat(saved.get("metadata").toString()).isEqualTo("{}");
    }

    @Test
    @DisplayName("이모지 제목은 코드 포인트 기준 10자까지 그대로 저장한다")
    void createPost_tenCodePointEmojiTitle_savesTitle() {
        // Given
        String title = "📸".repeat(10);
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);

        // When
        PostCreationResult result = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                title
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM posts WHERE id = ?",
                String.class,
                result.postId()
        )).isEqualTo(title);
    }

    @Test
    @DisplayName("제목이 10자를 초과하면 게시물을 생성할 수 없다")
    void createPost_tooLongTitle_throwsBusinessException() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        "12345678901"
                )
        );

        // Then
        assertThat(exception).hasMessage("제목은 10자 이하여야 합니다.");
        assertNoCreatedRows();
    }

    @Test
    @DisplayName("존재하지 않는 사용자는 게시물을 생성할 수 없다")
    void createPost_unknownUser_throwsNotFoundException() {
        // Given
        UUID unknownUserId = UUID.randomUUID();

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(
                        unknownUserId,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("게시물을 작성할 회원을 찾을 수 없습니다.");
        assertNoCreatedRows();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("차단된 사용자는 게시물을 생성할 수 없다")
    void createPost_bannedUser_throwsNotFoundException() {
        // Given
        jdbcTemplate.update("UPDATE users SET status = 'BANNED' WHERE id = ?", USER_ID);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("게시물을 작성할 회원을 찾을 수 없습니다.");
        assertNoCreatedRows();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("삭제된 주제에는 게시물을 생성할 수 없다")
    void createPost_deletedTopic_throwsNotFoundException() {
        // Given
        jdbcTemplate.update(
                "UPDATE topics SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                TOPIC_ID
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("게시물을 작성할 주제를 찾을 수 없습니다.");
        assertNoCreatedRows();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("참여 시작 전 주제에는 게시물을 생성할 수 없다")
    void createPost_beforeOpenTopic_throwsBusinessException() {
        // Given
        jdbcTemplate.update("""
                UPDATE topics
                SET starts_at = CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    ends_at = CURRENT_TIMESTAMP + INTERVAL '2 hours'
                WHERE id = ?
                """, TOPIC_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("현재 게시물을 작성할 수 없는 주제입니다.");
        assertNoCreatedRows();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("참여가 끝난 주제에는 게시물을 생성할 수 없다")
    void createPost_closedTopic_throwsBusinessException() {
        // Given
        jdbcTemplate.update("""
                UPDATE topics
                SET starts_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                    ends_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
                WHERE id = ?
                """, TOPIC_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("현재 게시물을 작성할 수 없는 주제입니다.");
        assertNoCreatedRows();
        then(postImageStorage).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("업로드한 사진이 없으면 게시물을 생성할 수 없다")
    void createPost_missingUploadedImage_throwsNotFoundException() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(false);

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("업로드한 사진을 찾을 수 없습니다.");
        assertNoCreatedRows();
    }

    @Test
    @DisplayName("같은 사용자는 열린 주제에 게시물을 중복 생성할 수 없다")
    void createPost_duplicateUserAndTopic_throwsBusinessException() {
        // Given
        UUID secondUploadId = UUID.randomUUID();
        insertUpload(secondUploadId, USER_ID, "ISSUED");
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.existsUploadedImage(secondUploadId)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(secondUploadId))
                .willReturn("chalkak/posts/test/original/" + secondUploadId + ".webp");
        postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        secondUploadId,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("이미 해당 주제에 게시물을 작성했습니다.");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM photos", Integer.class))
                .isEqualTo(1);
    }

    private void backdatePost(UUID postId, int minutes) {
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE posts SET created_at = CURRENT_TIMESTAMP - MAKE_INTERVAL(mins => ?)"
                        + " WHERE id = ?",
                minutes,
                postId
        );
        entityManager.clear();
    }

    private String moderationStatusOf(UUID postId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT CAST(moderation_status AS TEXT) FROM posts WHERE id = ?",
                String.class,
                postId
        );
    }

    private UUID givenSecondUpload() {
        UUID secondUploadId = UUID.randomUUID();
        insertUpload(secondUploadId, USER_ID, "ISSUED");
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.existsUploadedImage(secondUploadId)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(secondUploadId))
                .willReturn("chalkak/posts/test/original/" + secondUploadId + ".webp");

        return secondUploadId;
    }

    @Test
    @DisplayName("처리 대기 시간을 넘긴 검수 대기 게시물은 거절되고 같은 주제에 다시 작성할 수 있다")
    void createPost_stalledValidatingPost_rejectsItAndAllowsNewPost() {
        // Given
        UUID secondUploadId = givenSecondUpload();
        PostCreationResult stalled =
                postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);
        backdatePost(stalled.postId(), 31);

        // When
        PostCreationResult result =
                postCommandService.createPost(USER_ID, TOPIC_ID, secondUploadId, null);

        // Then
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.VALIDATING);
        assertThat(moderationStatusOf(stalled.postId())).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("처리 대기 시간 안의 검수 대기 게시물은 여전히 중복으로 막는다")
    void createPost_recentValidatingPost_throwsBusinessException() {
        // Given
        UUID secondUploadId = givenSecondUpload();
        PostCreationResult pending =
                postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);
        backdatePost(pending.postId(), 29);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(USER_ID, TOPIC_ID, secondUploadId, null)
        );

        // Then
        assertThat(exception).hasMessage("이미 해당 주제에 게시물을 작성했습니다.");
        assertThat(moderationStatusOf(pending.postId())).isEqualTo("VALIDATING");
    }

    @Test
    @DisplayName("거절된 게시물이 있으면 같은 주제에 다시 작성할 수 있다")
    void createPost_afterRejection_createsNewPost() {
        // Given
        UUID secondUploadId = UUID.randomUUID();
        insertUpload(secondUploadId, USER_ID, "ISSUED");
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        String secondStorageKey = "chalkak/posts/test/original/" + secondUploadId + ".webp";
        given(postImageStorage.existsUploadedImage(secondUploadId)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(secondUploadId))
                .willReturn(secondStorageKey);
        postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);
        postCommandService.failPostImageProcessing(PHOTO_UPLOAD_ID, "CORRUPTED_IMAGE");

        // When
        PostCreationResult result =
                postCommandService.createPost(USER_ID, TOPIC_ID, secondUploadId, null);

        // Then
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.VALIDATING);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("같은 사진 업로드는 다른 게시물에도 다시 사용할 수 없다")
    void createPost_reusedPhotoUpload_throwsBusinessException() {
        // Given
        insertSecondUserAndTopic();
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        SECOND_TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("이미 사용된 사진입니다.");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM photos", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("게시물에 연결되지 않은 사진이 같은 원본 키를 쓰고 있어도 사진 중복으로 막는다")
    void createPost_orphanPhotoWithSameStorageKey_throwsBusinessException() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, metadata, created_at, updated_at
                ) VALUES (?, ?, '{}'::jsonb, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), ORIGINAL_STORAGE_KEY);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(
                        USER_ID,
                        TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("이미 사용된 사진입니다.");
    }

    @Test
    @DisplayName("검수 중인 새 게시물은 공개 상세 조회에 노출되지 않는다")
    void createPost_validatingPost_isNotVisible() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        PostCreationResult created = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                null
        );
        entityManager.clear();

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postQueryService.getPost(created.postId(), USER_ID)
        );

        // Then
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("이미지 처리가 끝난 업로드로 만든 게시물은 바로 공개 상태가 된다")
    void createPost_readyUpload_savesApprovedPost() {
        // Given
        jdbcTemplate.update("""
                UPDATE post_image_uploads
                SET status = 'READY',
                    image_metadata = '{"width": 4032, "height": 3024}'::jsonb
                WHERE id = ?
                """, PHOTO_UPLOAD_ID);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.toThumbnailStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(THUMBNAIL_STORAGE_KEY);

        // When
        PostCreationResult result = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                null
        );
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.APPROVED);
        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT p.moderation_status, p.moderated_at,
                       ph.thumbnail_storage_key,
                       ph.metadata ->> 'width' AS metadata_width
                FROM posts p JOIN photos ph ON ph.id = p.photo_id
                WHERE p.id = ?
                """, result.postId());
        assertThat(saved.get("moderation_status").toString()).isEqualTo("APPROVED");
        assertThat(saved.get("moderated_at")).isNotNull();
        assertThat(saved.get("thumbnail_storage_key")).isEqualTo(THUMBNAIL_STORAGE_KEY);
        assertThat(saved.get("metadata_width")).isEqualTo("4032");
        then(postImageStorage).should(never()).existsUploadedImage(PHOTO_UPLOAD_ID);
    }

    @Test
    @DisplayName("게시물을 만들면 업로드 claim을 소비한다")
    void createPost_validRequest_claimsUpload() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);

        // When
        postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(jdbcTemplate.queryForObject("""
                SELECT claimed_at FROM post_image_uploads WHERE id = ?
                """, Instant.class, PHOTO_UPLOAD_ID)).isNotNull();
    }

    @Test
    @DisplayName("발급받지 않은 업로드 ID로는 게시물을 생성할 수 없다")
    void createPost_unknownUploadId_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(USER_ID, TOPIC_ID, UUID.randomUUID(), null)
        );

        // Then
        assertThat(exception).hasMessage("업로드한 사진을 찾을 수 없습니다.");
        assertNoCreatedRows();
    }

    @Test
    @DisplayName("다른 회원이 발급받은 업로드 ID는 존재하지 않는 것과 같이 다룬다")
    void createPost_otherUsersUploadId_throwsNotFoundException() {
        // Given
        insertSecondUserAndTopic();

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postCommandService.createPost(
                        SECOND_USER_ID,
                        SECOND_TOPIC_ID,
                        PHOTO_UPLOAD_ID,
                        null
                )
        );

        // Then
        assertThat(exception).hasMessage("업로드한 사진을 찾을 수 없습니다.");
        assertNoCreatedRows();
    }

    @Test
    @DisplayName("이미지 처리에서 거절된 업로드는 사유를 담아 거부한다")
    void createPost_rejectedUpload_throwsBusinessException() {
        // Given
        jdbcTemplate.update("""
                UPDATE post_image_uploads
                SET status = 'REJECTED', rejection_reason = 'UNSUPPORTED_FORMAT'
                WHERE id = ?
                """, PHOTO_UPLOAD_ID);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null)
        );

        // Then
        assertThat(exception).hasMessage("WebP 이미지만 업로드할 수 있습니다.");
        assertNoCreatedRows();
    }

    @Test
    @DisplayName("claim 유효 시간이 지난 업로드로는 게시물을 생성할 수 없다")
    void createPost_expiredUpload_throwsBusinessException() {
        // Given
        jdbcTemplate.update("""
                UPDATE post_image_uploads
                SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                WHERE id = ?
                """, PHOTO_UPLOAD_ID);
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postCommandService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null)
        );

        // Then
        assertThat(exception).hasMessage("사진 업로드 유효 시간이 지났습니다.");
        assertNoCreatedRows();
    }

    private void assertNoCreatedRows() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM photos", Integer.class))
                .isZero();
    }

    private void insertSecondUserAndTopic() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-create-second@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/signature-second.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, SECOND_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '두 번째 주제', CURRENT_DATE + 1,
                    CURRENT_TIMESTAMP - INTERVAL '1 hour',
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, SECOND_TOPIC_ID);
    }
}
