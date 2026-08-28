package com.chalkak.backend.post.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.photo.service.ImageUrlProvider;
import com.chalkak.backend.post.domain.PostImageUpload;
import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostImageUploadIssuer;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class PostImageUploadServiceTest extends IntegrationTestSupport {

    private static final UUID USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a4");
    private static final UUID BANNED_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570c9");
    private static final UUID WITHDRAWN_USER_ID =
            UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570a5");
    private static final String UPLOAD_URL = "https://test-bucket.s3.amazonaws.com/upload";

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
                    ?, 'post-upload@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/signature.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, USER_ID);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key,
                    created_at, updated_at, deleted_at
                ) VALUES (
                    ?, 'post-upload-withdrawn@example.com', 'ACTIVE',
                    'chalkak/signatures/test/original/signature-withdrawn.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, WITHDRAWN_USER_ID);
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, status, signature_original_storage_key, created_at, updated_at
                ) VALUES (
                    ?, 'post-upload-banned@example.com', 'BANNED',
                    'chalkak/signatures/test/original/signature-banned.png',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, BANNED_USER_ID);
        given(postImageUploadIssuer.issue(any(UUID.class))).willReturn(
                new PresignedPostImageUpload(UPLOAD_URL, 300L, "image/webp", 5_242_880L)
        );
    }

    @Test
    @DisplayName("활성 사용자에게 발급하면 ISSUED 업로드를 저장하고 발급 결과를 반환한다")
    void createPostImageUpload_activeUser_savesIssuedUpload() {
        // When
        PostImageUploadResult result = postCommandService.createPostImageUpload(USER_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        assertThat(result.uploadId()).isNotNull();
        assertThat(result.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(result.expiresInSeconds()).isEqualTo(300L);
        assertThat(result.contentType()).isEqualTo("image/webp");
        assertThat(result.maxBytes()).isEqualTo(5_242_880L);

        Map<String, Object> saved = jdbcTemplate.queryForMap("""
                SELECT user_id, status, rejection_reason, image_metadata,
                       expires_at, claimed_at
                FROM post_image_uploads
                WHERE id = ?
                """, result.uploadId());
        assertThat(saved.get("user_id")).isEqualTo(USER_ID);
        assertThat(saved.get("status").toString()).isEqualTo("ISSUED");
        assertThat(saved.get("rejection_reason")).isNull();
        assertThat(saved.get("image_metadata")).isNull();
        assertThat(saved.get("claimed_at")).isNull();
    }

    @Test
    @DisplayName("발급한 업로드는 claim 유효 시간 뒤에 만료된다")
    void createPostImageUpload_activeUser_expiresAfterClaimTtl() {
        // Given
        Instant issuedAt = Instant.now();

        // When
        PostImageUploadResult result = postCommandService.createPostImageUpload(USER_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        Instant expiresAt = jdbcTemplate.queryForObject("""
                SELECT expires_at FROM post_image_uploads WHERE id = ?
                """, Instant.class, result.uploadId());
        assertThat(expiresAt).isCloseTo(
                issuedAt.plus(PostImageUpload.CLAIM_TTL),
                within(10, ChronoUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("저장한 업로드 ID로 presigned URL을 발급한다")
    void createPostImageUpload_activeUser_issuesUrlForSavedUploadId() {
        // When
        PostImageUploadResult result = postCommandService.createPostImageUpload(USER_ID);
        entityManager.flush();
        entityManager.clear();

        // Then
        then(postImageUploadIssuer).should().issue(result.uploadId());
    }

    @Test
    @DisplayName("존재하지 않는 회원은 업로드를 발급받을 수 없다")
    void createPostImageUpload_unknownUser_throwsNotFound() {
        // When & Then
        assertThatThrownBy(() -> postCommandService.createPostImageUpload(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사진을 업로드할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("탈퇴한 회원은 업로드를 발급받을 수 없다")
    void createPostImageUpload_withdrawnUser_throwsNotFound() {
        // When & Then
        assertThatThrownBy(() -> postCommandService.createPostImageUpload(WITHDRAWN_USER_ID))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사진을 업로드할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("정지된 회원은 업로드 URL을 발급받지 못한다")
    void createPostImageUpload_bannedUser_throwsForbidden() {
        // When & Then
        assertThatThrownBy(() -> postCommandService.createPostImageUpload(BANNED_USER_ID))
                .isInstanceOf(ForbiddenException.class)
                .satisfies(exception -> assertThat(((ForbiddenException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.USER_BANNED));
    }

    @Test
    @DisplayName("정지된 회원의 발급 시도는 업로드 행을 남기지 않는다")
    void createPostImageUpload_bannedUser_leavesNoUploadRow() {
        // When
        assertThatThrownBy(() -> postCommandService.createPostImageUpload(BANNED_USER_ID))
                .isInstanceOf(ForbiddenException.class);

        // Then
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM post_image_uploads WHERE user_id = ?",
                Integer.class,
                BANNED_USER_ID
        )).isZero();
    }
}
