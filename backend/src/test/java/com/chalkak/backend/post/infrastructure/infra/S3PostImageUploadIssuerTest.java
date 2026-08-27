package com.chalkak.backend.post.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PostProcessingImageUpload;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3PostImageUploadIssuerTest {

    private static final String BUCKET = "test-bucket";
    private static final long MAX_BYTES = 5_242_880L;
    private static final String UPLOAD_URL = "https://s3.example.com/presigned";

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final PostImageStorage postImageStorage = mock(PostImageStorage.class);

    @Test
    @DisplayName("게시물 처리 결과의 원본과 썸네일 키에 덮어쓰기 방지 URL을 발급한다")
    void issueProcessingUpload_presignsOriginalAndThumbnailKeys() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        String originalKey = "chalkak/posts/dev/original/" + uploadId + ".webp";
        String thumbnailKey = "chalkak/posts/dev/thumbnail/" + uploadId + ".webp";
        PresignedPutObjectRequest originalRequest = presignedRequest("https://s3.test/original");
        PresignedPutObjectRequest thumbnailRequest = presignedRequest("https://s3.test/thumbnail");
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(originalRequest, thumbnailRequest);
        given(postImageStorage.toOriginalStorageKey(uploadId)).willReturn(originalKey);
        given(postImageStorage.toThumbnailStorageKey(uploadId)).willReturn(thumbnailKey);
        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        // When
        PostProcessingImageUpload upload = issuer().issueProcessingUpload(uploadId);

        // Then
        assertThat(upload.originalUploadUrl()).isEqualTo("https://s3.test/original");
        assertThat(upload.thumbnailUploadUrl()).isEqualTo("https://s3.test/thumbnail");
        assertThat(upload.contentType()).isEqualTo("image/webp");
        assertThat(upload.cacheControl()).isEqualTo("public, max-age=86400");
        verify(s3Presigner, times(2))
                .presignPutObject(requestCaptor.capture());
        assertProcessingRequest(requestCaptor.getAllValues().get(0), originalKey);
        assertProcessingRequest(requestCaptor.getAllValues().get(1), thumbnailKey);
    }

    @Test
    @DisplayName("개발 환경 게시물 WebP 업로드 URL을 5분 동안 유효하게 발급한다")
    void issue_devEnvironment_presignsDevPostUpload() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        String stagingStorageKey = "chalkak/staging/dev/posts/" + uploadId + ".webp";
        givenPresignedUrl();
        given(postImageStorage.toStagingStorageKey(uploadId)).willReturn(stagingStorageKey);
        S3PostImageUploadIssuer issuer = issuer();
        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        // When
        PresignedPostImageUpload upload = issuer.issue(uploadId);

        // Then
        assertThat(upload.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(upload.expiresInSeconds()).isEqualTo(300L);
        assertThat(upload.contentType()).isEqualTo("image/webp");
        assertThat(upload.maxBytes()).isEqualTo(MAX_BYTES);
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        PutObjectPresignRequest presignRequest = requestCaptor.getValue();
        PutObjectRequest putObjectRequest = presignRequest.putObjectRequest();
        assertThat(presignRequest.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.key()).isEqualTo(stagingStorageKey);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
    }

    @Test
    @DisplayName("운영 환경 업로드 URL은 운영 staging 키에 서명한다")
    void issue_prodEnvironment_presignsProdStagingKey() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        String stagingStorageKey = "chalkak/staging/prod/posts/" + uploadId + ".webp";
        givenPresignedUrl();
        given(postImageStorage.toStagingStorageKey(uploadId)).willReturn(stagingStorageKey);
        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        // When
        issuer().issue(uploadId);

        // Then
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        assertThat(requestCaptor.getValue().putObjectRequest().key())
                .isEqualTo(stagingStorageKey);
    }

    private void givenPresignedUrl() throws Exception {
        PresignedPutObjectRequest presignedRequest = presignedRequest(UPLOAD_URL);
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);
    }

    private PresignedPutObjectRequest presignedRequest(String url) throws Exception {
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(URI.create(url).toURL());
        return presignedRequest;
    }

    private void assertProcessingRequest(
            PutObjectPresignRequest presignRequest,
            String expectedKey
    ) {
        PutObjectRequest putObjectRequest = presignRequest.putObjectRequest();
        assertThat(presignRequest.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.key()).isEqualTo(expectedKey);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
        assertThat(putObjectRequest.cacheControl()).isEqualTo("public, max-age=86400");
        assertThat(putObjectRequest.ifNoneMatch()).isEqualTo("*");
    }

    private S3PostImageUploadIssuer issuer() {
        return new S3PostImageUploadIssuer(
                s3Presigner,
                postImageStorage,
                new ImageProperties(
                        BUCKET,
                        "ap-northeast-2",
                        "https://cdn.example.com",
                        "chalkak",
                        "dev",
                        null,
                        new ImageProperties.Post(MAX_BYTES, "public, max-age=86400"),
                        false
                )
        );
    }

    @Test
    @DisplayName("처리 결과 URL은 업로드에 필요한 헤더를 모두 서명한다")
    void issueProcessingUpload_presignedUrlSignsRequiredHeaders() {
        // Given
        UUID uploadId = UUID.randomUUID();
        S3Presigner realPresigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();

        // When
        PresignedPutObjectRequest presigned = realPresigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(PutObjectRequest.builder()
                                .bucket(BUCKET)
                                .key("chalkak/posts/dev/original/" + uploadId + ".webp")
                                .contentType("image/webp")
                                .cacheControl("public, max-age=86400")
                                .ifNoneMatch("*")
                                .build())
                        .build());

        // Then
        assertThat(presigned.signedHeaders()).containsKey("content-type");
        assertThat(presigned.signedHeaders()).containsKey("cache-control");
        assertThat(presigned.signedHeaders()).containsKey("if-none-match");
        assertThat(presigned.url().getQuery()).contains("content-type");
    }
}
