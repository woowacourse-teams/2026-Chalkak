package com.chalkak.backend.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhotoTest {

    @Test
    @DisplayName("원본 스토리지 키로 처리 전 사진을 생성한다")
    void createPhoto_originalStorageKey_createsUnprocessedPhoto() {
        // Given
        String originalStorageKey = "chalkak/posts/dev/original/upload-id.png";

        // When
        Photo photo = Photo.createPhoto(originalStorageKey);

        // Then
        assertThat(photo.getOriginalStorageKey()).isEqualTo(originalStorageKey);
        assertThat(photo.getThumbnailStorageKey()).isNull();
        assertThat(photo.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("원본 스토리지 키가 공백이면 사진을 생성할 수 없다")
    void createPhoto_blankOriginalStorageKey_throwsBusinessException() {
        // When & Then
        assertThatThrownBy(() -> Photo.createPhoto(" "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사진 저장 정보가 올바르지 않습니다.");
    }
    @Test
    @DisplayName("이미지 처리를 완료하면 썸네일 키와 메타데이터를 채운다")
    void completeProcessing_processedImage_fillsThumbnailKeyAndMetadata() {
        // Given
        Photo photo = Photo.createPhoto("chalkak/posts/test/original/photo.webp");
        Map<String, Object> metadata = Map.of("width", 4032, "height", 3024);

        // When
        photo.completeProcessing("chalkak/posts/test/thumbnail/photo.webp", metadata);

        // Then
        assertThat(photo.getThumbnailStorageKey())
                .isEqualTo("chalkak/posts/test/thumbnail/photo.webp");
        assertThat(photo.getMetadata()).isEqualTo(metadata);
    }

    @Test
    @DisplayName("메타데이터가 없어도 이미지 처리를 완료할 수 있다")
    void completeProcessing_nullMetadata_keepsEmptyMetadata() {
        // Given
        Photo photo = Photo.createPhoto("chalkak/posts/test/original/photo.webp");

        // When
        photo.completeProcessing("chalkak/posts/test/thumbnail/photo.webp", null);

        // Then
        assertThat(photo.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("사진을 삭제하면 soft delete 시각을 기록한다")
    void delete_activePhoto_recordsDeletedAt() {
        // Given
        Photo photo = Photo.createPhoto("chalkak/posts/test/original/photo.webp");
        Instant deletedAt = Instant.parse("2026-08-20T01:00:00Z");

        // When
        photo.delete(deletedAt);

        // Then
        assertThat(photo.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    @DisplayName("이미 삭제된 사진을 다시 삭제해도 최초 삭제 시각을 유지한다")
    void delete_alreadyDeletedPhoto_keepsFirstDeletionTime() {
        // Given
        Photo photo = Photo.createPhoto("chalkak/posts/test/original/photo.webp");
        Instant firstDeletedAt = Instant.parse("2026-08-20T01:00:00Z");
        photo.delete(firstDeletedAt);

        // When
        photo.delete(firstDeletedAt.plusSeconds(60));

        // Then
        assertThat(photo.getDeletedAt()).isEqualTo(firstDeletedAt);
    }
}
