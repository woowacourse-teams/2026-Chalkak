package com.chalkak.backend.post.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.post.repository.PostImageStorage;
import com.chalkak.backend.post.repository.PresignedPostImageUpload;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(URI.create(UPLOAD_URL).toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);
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
                        new ImageProperties.Post(MAX_BYTES),
                        false
                )
        );
    }
}
