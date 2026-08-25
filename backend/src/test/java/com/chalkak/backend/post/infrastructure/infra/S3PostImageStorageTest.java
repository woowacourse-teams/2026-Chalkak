package com.chalkak.backend.post.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3PostImageStorageTest {

    private static final String ROOT_PREFIX = "chalkak";

    private final S3Client s3Client = mock(S3Client.class);

    private final S3PostImageStorage postImageStorage = new S3PostImageStorage(
            s3Client,
            new ImageProperties(
                    "test-bucket",
                    "ap-northeast-2",
                    "https://cdn.example.com",
                    ROOT_PREFIX,
                    "dev",
                    new ImageProperties.Signature(1048576L, List.of("image/png")),
                    false
            )
    );

    @Test
    @DisplayName("업로드 ID로 게시물 staging 스토리지 키를 만든다")
    void toStagingStorageKey_uploadId_createsPostStagingKey() {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When
        String storageKey = postImageStorage.toStagingStorageKey(uploadId);

        // Then
        assertThat(storageKey)
                .isEqualTo(ROOT_PREFIX + "/staging/dev/posts/" + uploadId + ".webp");
    }

    @Test
    @DisplayName("업로드 ID로 게시물 원본 스토리지 키를 만든다")
    void toOriginalStorageKey_uploadId_createsPostOriginalKey() {
        // Given
        UUID uploadId = UUID.randomUUID();

        // When
        String storageKey = postImageStorage.toOriginalStorageKey(uploadId);

        // Then
        assertThat(storageKey)
                .isEqualTo(ROOT_PREFIX + "/posts/dev/original/" + uploadId + ".webp");
    }

    @Test
    @DisplayName("staging 객체가 있으면 업로드 이미지가 존재한다")
    @SuppressWarnings("unchecked")
    void existsUploadedImage_existingStagingObject_returnsTrue() {
        // Given
        UUID uploadId = UUID.randomUUID();
        given(s3Client.headObject(anyHeadRequest()))
                .willReturn(HeadObjectResponse.builder().build());
        ArgumentCaptor<Consumer<HeadObjectRequest.Builder>> requestCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        // When
        boolean exists = postImageStorage.existsUploadedImage(uploadId);

        // Then
        assertThat(exists).isTrue();
        verify(s3Client).headObject(requestCaptor.capture());
        HeadObjectRequest.Builder requestBuilder = HeadObjectRequest.builder();
        requestCaptor.getValue().accept(requestBuilder);
        assertThat(requestBuilder.build().bucket()).isEqualTo("test-bucket");
        assertThat(requestBuilder.build().key())
                .isEqualTo(ROOT_PREFIX + "/staging/dev/posts/" + uploadId + ".webp");
    }

    @Test
    @DisplayName("staging 객체가 없으면 업로드 이미지가 존재하지 않는다")
    void existsUploadedImage_noSuchKey_returnsFalse() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
                .willThrow(NoSuchKeyException.builder().build());

        // When & Then
        assertThat(postImageStorage.existsUploadedImage(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("HeadObject가 404를 반환하면 업로드 이미지가 존재하지 않는다")
    void existsUploadedImage_notFound_returnsFalse() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
                .willThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        // When & Then
        assertThat(postImageStorage.existsUploadedImage(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("ListBucket 권한이 없어 403이 와도 업로드 이미지가 존재하지 않는다")
    void existsUploadedImage_accessDenied_returnsFalse() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
                .willThrow(S3Exception.builder().statusCode(403).message("Forbidden").build());

        // When & Then
        assertThat(postImageStorage.existsUploadedImage(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("403과 404가 아닌 S3 오류는 전파한다")
    void existsUploadedImage_serverError_propagates() {
        // Given
        given(s3Client.headObject(anyHeadRequest()))
                .willThrow(S3Exception.builder().statusCode(500).message("Internal Error").build());

        // When & Then
        assertThatThrownBy(() -> postImageStorage.existsUploadedImage(UUID.randomUUID()))
                .isInstanceOf(S3Exception.class);
    }

    @SuppressWarnings("unchecked")
    private Consumer<HeadObjectRequest.Builder> anyHeadRequest() {
        return any(Consumer.class);
    }
}
