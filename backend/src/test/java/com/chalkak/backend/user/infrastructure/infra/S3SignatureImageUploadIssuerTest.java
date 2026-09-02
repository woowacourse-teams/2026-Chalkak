package com.chalkak.backend.user.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureProcessingImageUpload;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

class S3SignatureImageUploadIssuerTest {

    private static final String BUCKET = "test-bucket";
    private static final String ROOT_PREFIX = "chalkak";
    private static final String UPLOAD_URL = "https://s3.example.com/presigned";

    private final S3Presigner s3Presigner = mock(S3Presigner.class);
    private final SignatureImageStorage signatureImageStorage = mock(SignatureImageStorage.class);

    @Test
    @DisplayName("사인 처리 결과의 원본과 썸네일 키에 덮어쓰기 방지 URL을 발급한다")
    void issueProcessingUpload_presignsOriginalAndThumbnailKeys() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        String originalKey = "chalkak/signatures/dev/original/" + uploadId + ".png";
        String thumbnailKey = "chalkak/signatures/dev/thumbnail/" + uploadId + ".png";
        PresignedPutObjectRequest originalRequest = presignedRequest("https://s3.test/original");
        PresignedPutObjectRequest thumbnailRequest = presignedRequest("https://s3.test/thumbnail");
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(originalRequest, thumbnailRequest);
        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        originalKey,
                        thumbnailKey
                ));
        S3SignatureImageUploadIssuer issuer = new S3SignatureImageUploadIssuer(
                s3Presigner,
                signatureImageStorage,
                imageProperties("dev")
        );
        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        // When
        SignatureProcessingImageUpload upload = issuer.issueProcessingUpload(uploadId);

        // Then
        assertThat(upload.originalUploadUrl()).isEqualTo("https://s3.test/original");
        assertThat(upload.thumbnailUploadUrl()).isEqualTo("https://s3.test/thumbnail");
        assertThat(upload.contentType()).isEqualTo("image/png");
        assertThat(upload.cacheControl()).isEqualTo("public, max-age=86400");
        verify(s3Presigner, times(2))
                .presignPutObject(requestCaptor.capture());
        assertProcessingRequest(requestCaptor.getAllValues().get(0), originalKey);
        assertProcessingRequest(requestCaptor.getAllValues().get(1), thumbnailKey);
    }

    @Test
    @DisplayName("개발 환경 사인 PNG 업로드 URL을 5분 동안 유효하게 발급한다")
    void issue_devEnvironment_presignsDevSignatureUpload() throws Exception {
        // Given
        UUID uploadId = UUID.randomUUID();
        String stagingStorageKey =
                "chalkak/staging/dev/signatures/" + uploadId + ".png";
        PresignedPutObjectRequest presignedRequest = mock(PresignedPutObjectRequest.class);
        given(presignedRequest.url()).willReturn(URI.create(UPLOAD_URL).toURL());
        given(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
                .willReturn(presignedRequest);
        given(signatureImageStorage.toStagingStorageKey(uploadId))
                .willReturn(stagingStorageKey);
        S3SignatureImageUploadIssuer issuer = new S3SignatureImageUploadIssuer(
                s3Presigner,
                signatureImageStorage,
                imageProperties("dev")
        );
        ArgumentCaptor<PutObjectPresignRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);

        // When
        SignatureImageUpload upload = issuer.issue(uploadId);

        // Then
        assertThat(upload.uploadId()).isEqualTo(uploadId);
        assertThat(upload.uploadUrl()).isEqualTo(UPLOAD_URL);
        assertThat(upload.expiresInSeconds()).isEqualTo(300L);
        verify(s3Presigner).presignPutObject(requestCaptor.capture());
        PutObjectPresignRequest request = requestCaptor.getValue();
        PutObjectRequest putObjectRequest = request.putObjectRequest();
        assertThat(request.signatureDuration()).isEqualTo(Duration.ofMinutes(5));
        assertThat(putObjectRequest.bucket()).isEqualTo(BUCKET);
        assertThat(putObjectRequest.key()).isEqualTo(stagingStorageKey);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/png");
        verify(signatureImageStorage).toStagingStorageKey(uploadId);
    }

    private ImageProperties imageProperties(String environment) {
        return new ImageProperties(
                BUCKET,
                "ap-northeast-2",
                "https://cdn.example.com",
                ROOT_PREFIX,
                environment,
                new ImageProperties.Signature(
                        1_048_576L,
                        List.of("image/png"),
                        "public, max-age=86400"
                ),
                null,
                false
        );
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
        assertThat(putObjectRequest.contentType()).isEqualTo("image/png");
        assertThat(putObjectRequest.cacheControl()).isEqualTo("public, max-age=86400");
        assertThat(putObjectRequest.ifNoneMatch()).isEqualTo("*");
    }
}
