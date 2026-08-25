package com.chalkak.backend.user.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.user.domain.SignatureStorageKeys;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3SignatureImageStorageTest {

    private static final String ROOT_PREFIX = "chalkak";
    private static final String ENVIRONMENT = "dev";
    private static final String BASE_URL = "https://cdn.example.com";

    private final S3Client s3Client = mock(S3Client.class);

    private final S3SignatureImageStorage signatureImageStorage = new S3SignatureImageStorage(
        s3Client,
        new ImageProperties(
            "test-bucket",
            "ap-northeast-2",
            BASE_URL,
            ROOT_PREFIX,
            ENVIRONMENT,
            new ImageProperties.Signature(1048576L, List.of("image/png")),
            false
        )
    );

    @Test
    @DisplayName("업로드 ID로 원본과 썸네일의 결정적 스토리지 키를 만든다")
    void toStorageKeys_uploadId_createsOriginalAndThumbnailKeys() {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When
        SignatureStorageKeys storageKeys = signatureImageStorage.toStorageKeys(uploadId);

        // Then
        assertThat(storageKeys.originalStorageKey())
            .isEqualTo(ROOT_PREFIX + "/signatures/dev/original/" + uploadId + ".png");
        assertThat(storageKeys.thumbnailStorageKey())
            .isEqualTo(ROOT_PREFIX + "/signatures/dev/thumbnail/" + uploadId + ".png");
    }

    @Test
    @DisplayName("운영 환경은 prod 경로에 원본과 썸네일 키를 만든다")
    void toStorageKeys_prodEnvironment_createsProdKeys() {
        // Given
        UUID uploadId = UUID.randomUUID();
        S3SignatureImageStorage prodStorage = new S3SignatureImageStorage(
            s3Client,
            new ImageProperties(
                "test-bucket",
                "ap-northeast-2",
                BASE_URL,
                ROOT_PREFIX,
                "prod",
                new ImageProperties.Signature(1048576L, List.of("image/png")),
                false
            )
        );

        // When
        SignatureStorageKeys storageKeys = prodStorage.toStorageKeys(uploadId);

        // Then
        assertThat(storageKeys.originalStorageKey())
            .isEqualTo(ROOT_PREFIX + "/signatures/prod/original/" + uploadId + ".png");
        assertThat(storageKeys.thumbnailStorageKey())
            .isEqualTo(ROOT_PREFIX + "/signatures/prod/thumbnail/" + uploadId + ".png");
    }

    @Test
    @DisplayName("이미지 URL은 CloudFront 오리진 경로인 root-prefix를 제외하고 만든다")
    void toImageUrl_originalStorageKey_excludesRootPrefix() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String storageKey = signatureImageStorage.toStorageKeys(uploadId).originalStorageKey();

        // When
        String imageUrl = signatureImageStorage.toImageUrl(storageKey);

        // Then
        assertThat(imageUrl)
            .isEqualTo(BASE_URL + "/signatures/dev/original/" + uploadId + ".png");
    }

    @Test
    @DisplayName("썸네일 객체가 있으면 사인 처리가 완료된 것으로 판단한다")
    @SuppressWarnings("unchecked")
    void isProcessingCompleted_existingThumbnail_returnsTrue() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(s3Client.headObject(anyHeadRequest())).willReturn(HeadObjectResponse.builder()
            .contentType("image/png")
            .contentLength(100L)
            .build());
        ArgumentCaptor<Consumer<HeadObjectRequest.Builder>> requestCaptor =
            ArgumentCaptor.forClass(Consumer.class);

        // When
        boolean completed = signatureImageStorage.isProcessingCompleted(uploadId);

        // Then
        assertThat(completed).isTrue();
        verify(s3Client).headObject(requestCaptor.capture());
        HeadObjectRequest.Builder requestBuilder = HeadObjectRequest.builder();
        requestCaptor.getValue().accept(requestBuilder);
        assertThat(requestBuilder.build().key())
            .isEqualTo("chalkak/signatures/dev/thumbnail/" + uploadId + ".png");
    }

    @Test
    @DisplayName("root-prefix로 시작하지 않는 스토리지 키면 예외를 발생시킨다")
    void toImageUrl_storageKeyWithoutRootPrefix_throwsIllegalArgumentException() {
        // Given
        String storageKey = "signatures/original/" + UUID.randomUUID() + ".png";

        // When & Then
        assertThatThrownBy(() -> signatureImageStorage.toImageUrl(storageKey))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("스토리지 키는 root-prefix로 시작해야 합니다: " + ROOT_PREFIX);
    }

    @Test
    @DisplayName("객체가 없으면 빈 값을 반환한다")
    void findUploadedImage_noSuchKey_returnsEmpty() {
        // Given
        given(s3Client.headObject(anyHeadRequest())).willThrow(NoSuchKeyException.builder().build());

        // When & Then
        assertThat(signatureImageStorage.findUploadedImage(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("사인 업로드 이미지는 staging/dev/signatures 경로에서 조회한다")
    @SuppressWarnings("unchecked")
    void findUploadedImage_existingStagingImage_usesSignatureStagingPath() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(s3Client.headObject(anyHeadRequest())).willReturn(HeadObjectResponse.builder()
            .contentType("image/png")
            .contentLength(100L)
            .build());
        ArgumentCaptor<Consumer<HeadObjectRequest.Builder>> requestCaptor =
            ArgumentCaptor.forClass(Consumer.class);

        // When
        signatureImageStorage.findUploadedImage(uploadId);

        // Then
        verify(s3Client).headObject(requestCaptor.capture());
        HeadObjectRequest.Builder requestBuilder = HeadObjectRequest.builder();
        requestCaptor.getValue().accept(requestBuilder);
        assertThat(requestBuilder.build().key())
            .isEqualTo("chalkak/staging/dev/signatures/" + uploadId + ".png");
    }

    @Test
    @DisplayName("ListBucket 권한이 없어 403이 와도 빈 값을 반환한다")
    void findUploadedImage_accessDenied_returnsEmpty() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
            .willThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        // When & Then
        assertThat(signatureImageStorage.findUploadedImage(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("403과 404가 아닌 S3 오류는 전파한다")
    void findUploadedImage_serverError_propagates() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
            .willThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        // When & Then
        assertThatThrownBy(() -> signatureImageStorage.findUploadedImage(UUID.randomUUID()))
            .isInstanceOf(S3Exception.class);
    }

    @SuppressWarnings("unchecked")
    private Consumer<HeadObjectRequest.Builder> anyHeadRequest() {
        return any(Consumer.class);
    }
}
