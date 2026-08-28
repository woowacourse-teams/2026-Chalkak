package com.chalkak.backend.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.admin.domain.PostMediaDeletionStatus;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.ModerationStatus;
import com.chalkak.backend.post.domain.PostImageUploadStatus;
import com.chalkak.backend.support.IntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AdminPostQueryServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658101");
    private static final UUID SECOND_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658102");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658201");
    private static final UUID SECOND_TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658202");
    private static final UUID PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658301");
    private static final UUID SECOND_PHOTO_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658302");
    private static final UUID POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658401");
    private static final UUID SECOND_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658402");
    private static final UUID SECOND_POST_IMAGE_UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658501");
    private static final UUID UNKNOWN_POST_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b658499");

    private static final LocalDate TOPIC_DATE = LocalDate.of(2026, 8, 12);
    private static final LocalDate SECOND_TOPIC_DATE = LocalDate.of(2026, 8, 13);
    private static final Instant SECOND_POST_CREATED_AT =
            Instant.parse("2026-08-13T04:30:00Z");
    private static final Instant SECOND_POST_DELETED_AT =
            Instant.parse("2026-08-13T06:00:00Z");

    private static final String SECOND_ORIGINAL_STORAGE_KEY =
            "chalkak/dev/admin-posts/original-2.webp";
    private static final String SECOND_THUMBNAIL_STORAGE_KEY =
            "chalkak/dev/admin-posts/thumbnail-2.webp";
    private static final String SECOND_ORIGINAL_IMAGE_URL =
            "https://cdn.example.com/dev/admin-posts/original-2.webp";
    private static final String SECOND_THUMBNAIL_IMAGE_URL =
            "https://cdn.example.com/dev/admin-posts/thumbnail-2.webp";

    @Autowired
    private AdminPostQueryService adminPostQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ImageUrlProvider imageUrlProvider;

    @BeforeEach
    void setUp() {
        insertUsers();
        insertTopics();
        insertPhotos();
        insertPostImageUpload();
        insertPosts();
        insertMediaDeletionPlan();
        jdbcTemplate.update(
                "INSERT INTO post_likes (post_id, user_id) VALUES (?, ?)",
                SECOND_POST_ID,
                USER_ID
        );
    }

    @Test
    @DisplayName("필터가 없으면 삭제 여부와 관계없이 모든 상태의 게시물을 최신순으로 조회한다")
    void getPosts_withoutFilters_returnsAllStatusesIncludingDeleted() {
        // Given
        given(imageUrlProvider.getUrl(SECOND_ORIGINAL_STORAGE_KEY))
                .willReturn(SECOND_ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SECOND_THUMBNAIL_STORAGE_KEY))
                .willReturn(SECOND_THUMBNAIL_IMAGE_URL);

        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                null,
                null,
                null,
                null,
                null,
                null,
                AdminPostSort.CREATED_AT_DESC,
                1,
                1
        );

        // Then
        assertThat(result.currentPage()).isEqualTo(1);
        assertThat(result.pageSize()).isEqualTo(1);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.postId()).isEqualTo(SECOND_POST_ID);
            assertThat(post.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
            assertThat(post.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
            assertThat(post.likeCount()).isEqualTo(1L);
            assertThat(post.photo().originalImageUrl()).isNull();
            assertThat(post.photo().thumbnailImageUrl()).isNull();
        });
    }

    @Test
    @DisplayName("상태와 주제, 날짜, 작성자, 등록 시각 조건에 맞는 게시물만 조회한다")
    void getPosts_allFilters_returnsMatchingPost() {
        // When
        AdminPostListResult result = adminPostQueryService.getPosts(
                ModerationStatus.REJECTED,
                SECOND_TOPIC_ID,
                SECOND_TOPIC_DATE,
                SECOND_USER_ID,
                Instant.parse("2026-08-13T04:00:00Z"),
                Instant.parse("2026-08-13T05:00:00Z"),
                AdminPostSort.CREATED_AT_DESC,
                1,
                20
        );

        // Then
        assertThat(result.posts()).singleElement().satisfies(post -> {
            assertThat(post.postId()).isEqualTo(SECOND_POST_ID);
            assertThat(post.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
            assertThat(post.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
        });
    }

    @Test
    @DisplayName("등록 시각 시작이 종료보다 늦으면 잘못된 요청 예외를 발생시킨다")
    void getPosts_createdAtFromAfterCreatedAtTo_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminPostQueryService.getPosts(
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-08-13T05:00:00Z"),
                        Instant.parse("2026-08-13T04:00:00Z"),
                        AdminPostSort.CREATED_AT_DESC,
                        1,
                        20
                )
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("페이지 오프셋이 정수 범위를 넘으면 잘못된 요청 예외를 발생시킨다")
    void getPosts_pageOffsetOverflow_throwsBusinessException() {
        // When
        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> adminPostQueryService.getPosts(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        AdminPostSort.CREATED_AT_DESC,
                        Integer.MAX_VALUE,
                        100
                )
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    @Test
    @DisplayName("삭제된 거절 게시물도 작성자와 주제, 사진 메타데이터를 포함해 상세 조회한다")
    void getPost_deletedRejectedPost_returnsAdminDetail() {
        // Given
        given(imageUrlProvider.getUrl(SECOND_ORIGINAL_STORAGE_KEY))
                .willReturn(SECOND_ORIGINAL_IMAGE_URL);
        given(imageUrlProvider.getUrl(SECOND_THUMBNAIL_STORAGE_KEY))
                .willReturn(SECOND_THUMBNAIL_IMAGE_URL);

        // When
        AdminPostDetail result = adminPostQueryService.getPost(SECOND_POST_ID);

        // Then
        assertThat(result.postId()).isEqualTo(SECOND_POST_ID);
        assertThat(result.moderationStatus()).isEqualTo(ModerationStatus.REJECTED);
        assertThat(result.deletedAt()).isEqualTo(SECOND_POST_DELETED_AT);
        assertThat(result.author().userId()).isEqualTo(SECOND_USER_ID);
        assertThat(result.topic().topicId()).isEqualTo(SECOND_TOPIC_ID);
        assertThat(result.photo().photoId()).isEqualTo(SECOND_PHOTO_ID);
        assertThat(result.photo().originalImageUrl()).isNull();
        assertThat(result.photo().thumbnailImageUrl()).isNull();
        assertThat(result.photo().metadata()).containsEntry("width", 2048);
        assertThat(result.photo().metadata())
                .doesNotContainKeys("location", "capturedAt", "metaAttributes");
        assertThat(result.imageUpload().uploadId()).isEqualTo(SECOND_POST_IMAGE_UPLOAD_ID);
        assertThat(result.imageUpload().status()).isEqualTo(PostImageUploadStatus.REJECTED);
        assertThat(result.imageUpload().rejectionReason()).isEqualTo("CORRUPTED_IMAGE");
        assertThat(result.mediaDeletion().status())
                .isEqualTo(PostMediaDeletionStatus.FAILED);
        assertThat(result.mediaDeletion().attemptCount()).isEqualTo(1);
        assertThat(result.mediaDeletion().lastErrorCode())
                .isEqualTo("STORAGE_DELETE_FAILED");
        assertThat(result.mediaDeletion().nextAttemptAt()).isNotNull();
        assertThat(result.mediaDeletion().completedAt()).isNull();
        assertThat(result.likeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 관리자 게시물 상세를 조회하면 찾을 수 없음 예외를 발생시킨다")
    void getPost_unknownPost_throwsNotFoundException() {
        // When
        NotFoundException exception = catchThrowableOfType(
                NotFoundException.class,
                () -> adminPostQueryService.getPost(UNKNOWN_POST_ID)
        );

        // Then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR);
    }

    private void insertUsers() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    signature_thumbnail_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'admin-post-user-1@example.com', 'ACTIVE',
                    'chalkak/dev/admin-posts/signature-1.webp',
                    'chalkak/dev/admin-posts/signature-thumbnail-1.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ), (
                    ?, 'admin-post-user-2@example.com', 'BANNED',
                    'chalkak/dev/admin-posts/signature-2.webp',
                    'chalkak/dev/admin-posts/signature-thumbnail-2.webp',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID, SECOND_USER_ID);
    }

    private void insertTopics() {
        jdbcTemplate.update("""
                INSERT INTO topics (
                    id, title, topic_date, starts_at, ends_at, created_at, updated_at
                ) VALUES (
                    ?, '첫 번째 관리자 조회 주제', ?,
                    '2026-08-12T00:00:00Z', '2026-08-13T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ), (
                    ?, '두 번째 관리자 조회 주제', ?,
                    '2026-08-13T00:00:00Z', '2026-08-14T00:00:00Z',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, TOPIC_ID, TOPIC_DATE, SECOND_TOPIC_ID, SECOND_TOPIC_DATE);
    }

    private void insertPhotos() {
        jdbcTemplate.update("""
                INSERT INTO photos (
                    id, original_storage_key, thumbnail_storage_key, metadata,
                    created_at, updated_at
                ) VALUES (
                    ?, 'chalkak/dev/admin-posts/original-1.webp',
                    'chalkak/dev/admin-posts/thumbnail-1.webp',
                    CAST('{"width": 1024, "height": 768}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                ), (
                    ?, ?, ?, CAST('{"width":2048,"height":1536,"byteSize":812345,
                    "location":{"latitude":37.5665,"longitude":126.978},
                    "capturedAt":"2026-08-13T11:02:31+09:00",
                    "metaAttributes":{"cameraModel":"private-device"}}' AS jsonb),
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, PHOTO_ID, SECOND_PHOTO_ID, SECOND_ORIGINAL_STORAGE_KEY,
                SECOND_THUMBNAIL_STORAGE_KEY);
    }

    private void insertPosts() {
        jdbcTemplate.update("""
                INSERT INTO posts (
                    id, user_id, topic_id, photo_id, post_image_upload_id,
                    title, moderation_status,
                    moderated_at, created_at, updated_at, deleted_at
                ) VALUES (
                    ?, ?, ?, ?, NULL, '검수 대기', 'PENDING', NULL,
                    '2026-08-12T03:30:00Z', '2026-08-12T03:30:00Z', NULL
                ), (
                    ?, ?, ?, ?, ?, '삭제된 작품', 'REJECTED',
                    '2026-08-13T05:00:00Z', ?, '2026-08-13T05:00:00Z', ?
                )
                """,
                POST_ID,
                USER_ID,
                TOPIC_ID,
                PHOTO_ID,
                SECOND_POST_ID,
                SECOND_USER_ID,
                SECOND_TOPIC_ID,
                SECOND_PHOTO_ID,
                SECOND_POST_IMAGE_UPLOAD_ID,
                Timestamp.from(SECOND_POST_CREATED_AT),
                Timestamp.from(SECOND_POST_DELETED_AT)
        );
    }

    private void insertPostImageUpload() {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, rejection_reason, image_metadata,
                    expires_at, claimed_at, created_at, updated_at
                ) VALUES (
                    ?, ?, 'REJECTED', 'CORRUPTED_IMAGE', NULL,
                    '2026-08-13T05:30:00Z', '2026-08-13T04:30:00Z',
                    '2026-08-13T04:00:00Z', '2026-08-13T04:20:00Z'
                )
                """, SECOND_POST_IMAGE_UPLOAD_ID, SECOND_USER_ID);
    }

    private void insertMediaDeletionPlan() {
        jdbcTemplate.update("""
                INSERT INTO post_media_deletion_plans (
                    post_id, post_image_upload_id,
                    staging_storage_key, original_storage_key, thumbnail_storage_key,
                    status, attempt_count, last_error_code,
                    next_attempt_at, completed_at, created_at, updated_at
                ) VALUES (
                    ?, ?,
                    'chalkak/staging/test/posts/deleted.webp', ?, ?,
                    CAST('FAILED' AS post_media_deletion_status), 1,
                    'STORAGE_DELETE_FAILED',
                    CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                SECOND_POST_ID,
                SECOND_POST_IMAGE_UPLOAD_ID,
                SECOND_ORIGINAL_STORAGE_KEY,
                SECOND_THUMBNAIL_STORAGE_KEY
        );
    }
}
