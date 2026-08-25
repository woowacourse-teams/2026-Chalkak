package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
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
            "chalkak/posts/test/original/" + PHOTO_UPLOAD_ID + ".png";

    @Autowired
    private PostService postService;

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
    }

    @Test
    @DisplayName("활성 사용자가 열린 주제에 업로드한 사진으로 게시물을 생성한다")
    void createPost_validRequest_savesPhotoAndValidatingPost() {
        // Given
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);

        // When
        PostCreationResult result = postService.createPost(
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
        PostCreationResult result = postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
                () -> postService.createPost(
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
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.existsUploadedImage(secondUploadId)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(secondUploadId))
                .willReturn("chalkak/posts/test/original/" + secondUploadId + ".png");
        postService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postService.createPost(
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

    @Test
    @DisplayName("같은 사진 업로드는 다른 게시물에도 다시 사용할 수 없다")
    void createPost_reusedPhotoUpload_throwsBusinessException() {
        // Given
        insertSecondUserAndTopic();
        given(postImageStorage.existsUploadedImage(PHOTO_UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(PHOTO_UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        postService.createPost(USER_ID, TOPIC_ID, PHOTO_UPLOAD_ID, null);

        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> postService.createPost(
                        SECOND_USER_ID,
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
                () -> postService.createPost(
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
        PostCreationResult created = postService.createPost(
                USER_ID,
                TOPIC_ID,
                PHOTO_UPLOAD_ID,
                null
        );
        entityManager.clear();

        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> postService.getPost(created.postId())
        );

        // Then
        assertThat(exception).hasMessage("게시물을 찾을 수 없습니다.");
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
