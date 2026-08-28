package com.chalkak.backend.post.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.user.infrastructure.infra.ImageProperties;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
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
                    new ImageProperties.Signature(
                            1048576L,
                            List.of("image/png"),
                            "public, max-age=86400"
                    ),
                    new ImageProperties.Post(5_242_880L, "public, max-age=86400"),
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

    @ParameterizedTest
    @MethodSource("supportedPostImageKeys")
    @DisplayName("현재 환경의 게시물 staging·원본·썸네일 이미지를 삭제한다")
    @SuppressWarnings("unchecked")
    void deleteImage_supportedPostImageKey_deletesConfiguredBucketObject(String storageKey) {
        // Given
        given(s3Client.deleteObject(anyDeleteRequest()))
                .willReturn(DeleteObjectResponse.builder().build());
        ArgumentCaptor<Consumer<DeleteObjectRequest.Builder>> requestCaptor =
                ArgumentCaptor.forClass(Consumer.class);

        // When
        postImageStorage.deleteImage(storageKey);

        // Then
        verify(s3Client).deleteObject(requestCaptor.capture());
        DeleteObjectRequest.Builder requestBuilder = DeleteObjectRequest.builder();
        requestCaptor.getValue().accept(requestBuilder);
        assertThat(requestBuilder.build().bucket()).isEqualTo("test-bucket");
        assertThat(requestBuilder.build().key()).isEqualTo(storageKey);
    }

    @ParameterizedTest
    @MethodSource("unsupportedImageKeys")
    @DisplayName("게시물 삭제 범위를 벗어난 스토리지 키는 S3 요청 전에 차단한다")
    void deleteImage_unsupportedStorageKey_throwsBusinessException(String storageKey) {
        // When & Then
        assertThatThrownBy(() -> postImageStorage.deleteImage(storageKey))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.BUSINESS_ERROR));
        verifyNoInteractions(s3Client);
    }

    @Test
    @DisplayName("삭제할 게시물 이미지가 이미 없으면 성공으로 처리한다")
    void deleteImage_noSuchKey_completesNormally() {
        // Given
        given(s3Client.deleteObject(anyDeleteRequest()))
                .willThrow(NoSuchKeyException.builder().build());

        // When & Then
        assertThatCode(() -> postImageStorage.deleteImage(originalStorageKey()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DeleteObject가 404를 반환하면 성공으로 처리한다")
    void deleteImage_notFound_completesNormally() {
        // Given
        given(s3Client.deleteObject(anyDeleteRequest()))
                .willThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        // When & Then
        assertThatCode(() -> postImageStorage.deleteImage(originalStorageKey()))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 500, 503})
    @DisplayName("DeleteObject의 권한 오류와 서버 오류는 호출자에게 전파한다")
    void deleteImage_accessDeniedOrServerError_propagates(int statusCode) {
        // Given
        RuntimeException s3Exception = S3Exception.builder()
                .statusCode(statusCode)
                .message("Delete failed")
                .build();
        given(s3Client.deleteObject(anyDeleteRequest())).willThrow(s3Exception);

        // When & Then
        assertThatThrownBy(() -> postImageStorage.deleteImage(originalStorageKey()))
                .isSameAs(s3Exception);
    }

    private static Stream<String> supportedPostImageKeys() {
        UUID imageId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
        return Stream.of(
                ROOT_PREFIX + "/staging/dev/posts/" + imageId + ".webp",
                ROOT_PREFIX + "/posts/dev/original/" + imageId + ".webp",
                ROOT_PREFIX + "/posts/dev/thumbnail/" + imageId + ".webp"
        );
    }

    private static Stream<String> unsupportedImageKeys() {
        UUID imageId = UUID.fromString("0198f6c1-62ba-7d30-8b12-0f733b6570d4");
        return Stream.of(
                null,
                "",
                "   ",
                ROOT_PREFIX + "/signatures/dev/original/" + imageId + ".png",
                ROOT_PREFIX + "/staging/dev/signatures/" + imageId + ".png",
                ROOT_PREFIX + "/posts/prod/original/" + imageId + ".webp",
                ROOT_PREFIX + "/posts/dev/original/../thumbnail/" + imageId + ".webp",
                ROOT_PREFIX + "/posts/dev/original/not-a-uuid.webp",
                ROOT_PREFIX + "/posts/dev/original/1-1-1-1-1.webp",
                ROOT_PREFIX + "/posts/dev/original/" + imageId + ".png",
                "other/posts/dev/original/" + imageId + ".webp"
        );
    }

    private String originalStorageKey() {
        return ROOT_PREFIX + "/posts/dev/original/" + UUID.randomUUID() + ".webp";
    }

    @SuppressWarnings("unchecked")
    private Consumer<HeadObjectRequest.Builder> anyHeadRequest() {
        return any(Consumer.class);
    }

    @SuppressWarnings("unchecked")
    private Consumer<DeleteObjectRequest.Builder> anyDeleteRequest() {
        return any(Consumer.class);
    }
}
