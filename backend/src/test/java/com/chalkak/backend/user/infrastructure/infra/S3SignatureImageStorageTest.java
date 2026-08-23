package com.chalkak.backend.user.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;

class S3SignatureImageStorageTest {

    private static final String ROOT_PREFIX = "chalkak";
    private static final String BASE_URL = "https://cdn.example.com";

    private final S3SignatureImageStorage signatureImageStorage = new S3SignatureImageStorage(
            mock(S3Client.class),
            new ImageProperties(
                    "test-bucket",
                    "ap-northeast-2",
                    BASE_URL,
                    ROOT_PREFIX,
                    new ImageProperties.Signature(1048576L, List.of("image/png"))
            )
    );

    @Test
    @DisplayName("이미지 URL은 CloudFront 오리진 경로인 root-prefix를 제외하고 만든다")
    void toImageUrl_originalStorageKey_excludesRootPrefix() {
        // Given
        UUID uploadId = UUID.randomUUID();
        String storageKey = signatureImageStorage.toOriginalStorageKey(uploadId);

        // When
        String imageUrl = signatureImageStorage.toImageUrl(storageKey);

        // Then
        assertThat(imageUrl)
                .isEqualTo(BASE_URL + "/signatures/original/" + uploadId + ".png");
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
}