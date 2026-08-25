package com.chalkak.backend.photo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
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
}
