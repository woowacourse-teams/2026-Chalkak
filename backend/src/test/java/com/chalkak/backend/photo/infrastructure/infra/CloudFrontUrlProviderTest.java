package com.chalkak.backend.photo.infrastructure.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CloudFrontUrlProviderTest {

    private final CloudFrontUrlProvider cloudFrontUrlProvider = new CloudFrontUrlProvider(
            URI.create("https://cdn.example.com"),
            "/chalkak"
    );

    @Test
    @DisplayName("스토리지 키의 원본 경로를 제거하고 CloudFront URL을 반환한다")
    void getUrl_storageKeyWithOriginPath_returnsCloudFrontUrl() {
        // Given
        String storageKey = "chalkak/dev/posts/photo-id/original.jpg";

        // When
        String result = cloudFrontUrlProvider.getUrl(storageKey);

        // Then
        assertThat(result).isEqualTo("https://cdn.example.com/dev/posts/photo-id/original.jpg");
    }

    @Test
    @DisplayName("원본 경로가 없는 스토리지 키면 예외를 발생시킨다")
    void getUrl_storageKeyWithoutOriginPath_throwsIllegalArgumentException() {
        // Given
        String storageKey = "dev/posts/photo-id/thumbnail.jpg";

        // When & Then
        assertThatThrownBy(() -> cloudFrontUrlProvider.getUrl(storageKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("스토리지 키는 원본 경로로 시작해야 합니다: /chalkak");
    }

    @Test
    @DisplayName("한글과 공백이 포함된 스토리지 키를 URL 인코딩한다")
    void getUrl_koreanAndSpaceStorageKey_returnsEncodedUrl() {
        // Given
        String storageKey = "chalkak/posts/original/테스트 사진.webp";

        // When
        String result = cloudFrontUrlProvider.getUrl(storageKey);

        // Then
        assertThat(result).isEqualTo(
                "https://cdn.example.com/posts/original/"
                        + "%ED%85%8C%EC%8A%A4%ED%8A%B8%20%EC%82%AC%EC%A7%84.webp"
        );
    }

    @Test
    @DisplayName("스토리지 키가 null이면 null을 반환한다")
    void getUrl_nullStorageKey_returnsNull() {
        // When
        String result = cloudFrontUrlProvider.getUrl(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("스토리지 키가 비어 있으면 예외를 발생시킨다")
    void getUrl_blankStorageKey_throwsIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> cloudFrontUrlProvider.getUrl(" / "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("스토리지 키는 비어 있을 수 없습니다.");
    }
}
