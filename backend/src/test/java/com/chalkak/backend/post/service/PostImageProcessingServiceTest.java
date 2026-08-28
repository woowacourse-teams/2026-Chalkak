package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PostProcessingImageUpload;
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
class PostImageProcessingServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a6");
    private static final UUID TOPIC_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570b6");
    private static final UUID UPLOAD_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c6");
    private static final String ORIGINAL_STORAGE_KEY =
            "chalkak/posts/test/original/" + UPLOAD_ID + ".webp";
    private static final String THUMBNAIL_STORAGE_KEY =
            "chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".webp";
    private static final Map<String, Object> METADATA = Map.of(
            "width", 4032,
            "height", 3024,
            "capturedAt", "2026-08-20T11:02:31+09:00"
    );

    @Autowired
    private PostCommandService postCommandService;

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

    @MockitoBean
    private PostImageUploadIssuer postImageUploadIssuer;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-processing@example.com', 'ACTIVE',
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
        insertUpload("ISSUED");
        given(postImageStorage.existsUploadedImage(UPLOAD_ID)).willReturn(true);
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn(ORIGINAL_STORAGE_KEY);
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn(THUMBNAIL_STORAGE_KEY);
    }

    private void insertUpload(String status) {
        jdbcTemplate.update("""
                INSERT INTO post_image_uploads (
                    id, user_id, status, expires_at, created_at, updated_at
                ) VALUES (
                    ?, ?, CAST(? AS post_image_upload_status),
                    CURRENT_TIMESTAMP + INTERVAL '1 hour',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
                """, UPLOAD_ID, USER_ID, status);
    }

    private UUID createValidatingPost() {
        PostCreationResult result = postCommandService.createPost(
                USER_ID,
                TOPIC_ID,
                UPLOAD_ID,
                "오늘의 기록"
        );
        entityManager.flush();
        entityManager.clear();
        return result.postId();
    }

    private Map<String, Object> findUpload() {
        return jdbcTemplate.queryForMap("""
                SELECT status, rejection_reason,
                       image_metadata ->> 'width' AS metadata_width
                FROM post_image_uploads WHERE id = ?
                """, UPLOAD_ID);
    }

    private Map<String, Object> findPost(UUID postId) {
        return jdbcTemplate.queryForMap("""
                SELECT p.moderation_status, p.moderated_at,
                       ph.thumbnail_storage_key,
                       ph.metadata ->> 'height' AS metadata_height
                FROM posts p JOIN photos ph ON ph.id = p.photo_id
                WHERE p.id = ?
                """, postId);
    }

    @Test
    @DisplayName("완료 콜백은 업로드를 READY로 올리고 메타데이터를 기록한다")
    void completePostImageProcessing_issuedUpload_marksReady() {
        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> upload = findUpload();
        assertThat(upload.get("status").toString()).isEqualTo("READY");
        assertThat(upload.get("metadata_width")).isEqualTo("4032");
    }

    @Test
    @DisplayName("게시물이 삭제되기 전에는 Lambda 처리 결과용 업로드 URL을 발급한다")
    void issuePostImageProcessingUpload_activeUpload_issuesUrls() {
        // Given
        PostProcessingImageUpload processingUpload = new PostProcessingImageUpload(
                "https://s3.test/original",
                "https://s3.test/thumbnail",
                "image/webp",
                "public, max-age=86400"
        );
        given(postImageUploadIssuer.issueProcessingUpload(UPLOAD_ID))
                .willReturn(processingUpload);

        // When
        PostProcessingImageUpload result =
                postCommandService.issuePostImageProcessingUpload(UPLOAD_ID);

        // Then
        assertThat(result).isEqualTo(processingUpload);
        then(postImageUploadIssuer).should().issueProcessingUpload(UPLOAD_ID);
    }

    @Test
    @DisplayName("삭제된 게시물의 업로드에는 처리 결과용 URL을 다시 발급하지 않는다")
    void issuePostImageProcessingUpload_deletedPost_throwsNotFound() {
        // Given
        UUID postId = createValidatingPost();
        jdbcTemplate.update(
                "UPDATE posts SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?",
                postId
        );
        entityManager.clear();

        // When
        NotFoundException exception = catchThrowableOfType(
                () -> postCommandService.issuePostImageProcessingUpload(UPLOAD_ID),
                NotFoundException.class
        );

        // Then
        assertThat(exception).isNotNull();
        then(postImageUploadIssuer).should(never()).issueProcessingUpload(UPLOAD_ID);
    }

    @Test
    @DisplayName("존재하지 않는 업로드에는 처리 결과용 URL을 발급하지 않는다")
    void issuePostImageProcessingUpload_unknownUpload_throwsNotFound() {
        // Given
        UUID unknownUploadId = UUID.fromString(
                "0198f6c1-62ba-7d30-8b12-0f733b6570ff"
        );

        // When
        NotFoundException exception = catchThrowableOfType(
                () -> postCommandService.issuePostImageProcessingUpload(unknownUploadId),
                NotFoundException.class
        );

        // Then
        assertThat(exception).isNotNull();
        then(postImageUploadIssuer).should(never()).issueProcessingUpload(unknownUploadId);
    }

    @Test
    @DisplayName("검수 중인 게시물이 있으면 완료 콜백이 관리자 검수 대기로 전환한다")
    void completePostImageProcessing_validatingPost_requestsModeration() {
        // Given
        UUID postId = createValidatingPost();

        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> post = findPost(postId);
        assertThat(post.get("moderation_status").toString()).isEqualTo("PENDING");
        assertThat(post.get("moderated_at")).isNull();
        assertThat(post.get("thumbnail_storage_key")).isEqualTo(THUMBNAIL_STORAGE_KEY);
        assertThat(post.get("metadata_height")).isEqualTo("3024");
    }

    @Test
    @DisplayName("스토리지 키 규칙이 바뀌어도 완료 콜백이 게시물을 찾는다")
    void completePostImageProcessing_changedStorageKeyRule_stillRequestsModeration() {
        // Given
        UUID postId = createValidatingPost();
        given(postImageStorage.toOriginalStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/original/" + UPLOAD_ID + ".avif");
        given(postImageStorage.toThumbnailStorageKey(UPLOAD_ID))
                .willReturn("chalkak/posts/test/thumbnail/" + UPLOAD_ID + ".avif");

        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> post = findPost(postId);
        assertThat(post.get("moderation_status").toString()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("실패 콜백은 업로드를 REJECTED로 내리고 사유를 기록한다")
    void failPostImageProcessing_issuedUpload_marksRejected() {
        // When
        postCommandService.failPostImageProcessing(UPLOAD_ID, "UNSUPPORTED_FORMAT");
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> upload = findUpload();
        assertThat(upload.get("status").toString()).isEqualTo("REJECTED");
        assertThat(upload.get("rejection_reason")).isEqualTo("UNSUPPORTED_FORMAT");
    }

    @Test
    @DisplayName("검수 중인 게시물이 있으면 실패 콜백이 REJECTED로 내린다")
    void failPostImageProcessing_validatingPost_rejectsPost() {
        // Given
        UUID postId = createValidatingPost();

        // When
        postCommandService.failPostImageProcessing(UPLOAD_ID, "TOO_LARGE");
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> post = findPost(postId);
        assertThat(post.get("moderation_status").toString()).isEqualTo("REJECTED");
        assertThat(post.get("moderated_at")).isNull();
        assertThat(post.get("thumbnail_storage_key")).isNull();
    }

    @Test
    @DisplayName("이미 실패한 업로드에 완료 콜백이 오면 게시물을 승격하지 않는다")
    void completePostImageProcessing_rejectedUpload_keepsPostRejected() {
        // Given
        UUID postId = createValidatingPost();
        postCommandService.failPostImageProcessing(UPLOAD_ID, "TOO_LARGE");
        entityManager.flush();
        entityManager.clear();

        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(findUpload().get("status").toString()).isEqualTo("REJECTED");
        assertThat(findPost(postId).get("moderation_status").toString())
                .isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("완료 콜백을 중복으로 받아도 최초 메타데이터와 검수 대기 상태를 유지한다")
    void completePostImageProcessing_duplicateCallback_staysPending() {
        // Given
        UUID postId = createValidatingPost();
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();
        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, Map.of("width", 1));
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> post = findPost(postId);
        assertThat(post.get("moderation_status").toString()).isEqualTo("PENDING");
        assertThat(post.get("moderated_at")).isNull();
        assertThat(findUpload().get("metadata_width")).isEqualTo("4032");
    }

    @Test
    @DisplayName("완료 뒤 실패 콜백이 와도 업로드와 게시물은 검수 대기 상태를 유지한다")
    void failPostImageProcessing_readyUpload_keepsPostPending() {
        // Given
        UUID postId = createValidatingPost();
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // When
        postCommandService.failPostImageProcessing(UPLOAD_ID, "TOO_LARGE");
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(findUpload().get("status").toString()).isEqualTo("READY");
        assertThat(findPost(postId).get("moderation_status").toString())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("실패 콜백을 중복으로 받아도 이미지 실패 거절 상태를 유지한다")
    void failPostImageProcessing_duplicateCallback_staysImageRejected() {
        // Given
        UUID postId = createValidatingPost();
        postCommandService.failPostImageProcessing(UPLOAD_ID, "TOO_LARGE");
        entityManager.flush();
        entityManager.clear();

        // When
        postCommandService.failPostImageProcessing(UPLOAD_ID, "PROCESSING_ERROR");
        entityManager.flush();
        entityManager.clear();

        // Then
        Map<String, Object> post = findPost(postId);
        assertThat(findUpload().get("rejection_reason")).isEqualTo("TOO_LARGE");
        assertThat(post.get("moderation_status").toString()).isEqualTo("REJECTED");
        assertThat(post.get("moderated_at")).isNull();
    }

    @Test
    @DisplayName("게시물이 아직 없어도 완료 콜백은 업로드 상태만 바꾸고 끝난다")
    void completePostImageProcessing_withoutPost_marksReadyOnly() {
        // When
        postCommandService.completePostImageProcessing(UPLOAD_ID, METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(findUpload().get("status").toString()).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM posts", Integer.class)).isZero();
    }

    @Test
    @DisplayName("존재하지 않는 업로드 ID의 콜백은 아무것도 바꾸지 않는다")
    void completePostImageProcessing_unknownUploadId_doesNothing() {
        // When
        postCommandService.completePostImageProcessing(UUID.randomUUID(), METADATA);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(findUpload().get("status").toString()).isEqualTo("ISSUED");
    }
}
