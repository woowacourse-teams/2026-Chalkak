package com.chalkak.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignatureImagePolicyTest {

    private static final long MAX_BYTES = 1024L;

    private final SignatureImagePolicy policy =
            new SignatureImagePolicy(MAX_BYTES, List.of("image/png"));

    @Test
    @DisplayName("허용 형식이고 크기 제한 이내면 통과한다")
    void validate_allowedImage_doesNotThrow() {
        // Given
        StoredImageMetadata image = new StoredImageMetadata("image/png", MAX_BYTES);

        // When & Then
        assertThatCode(() -> policy.validate(image)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("허용하지 않는 형식이면 예외를 발생시킨다")
    void validate_disallowedContentType_throwsException() {
        // Given
        StoredImageMetadata image = new StoredImageMetadata("image/jpeg", MAX_BYTES);

        // When & Then
        assertThatThrownBy(() -> policy.validate(image))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }

    @Test
    @DisplayName("형식을 알 수 없으면 예외를 발생시킨다")
    void validate_unknownContentType_throwsException() {
        // Given
        StoredImageMetadata image = new StoredImageMetadata(null, MAX_BYTES);

        // When & Then
        assertThatThrownBy(() -> policy.validate(image))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }

    @Test
    @DisplayName("크기 제한을 넘으면 예외를 발생시킨다")
    void validate_tooLargeImage_throwsException() {
        // Given
        StoredImageMetadata image = new StoredImageMetadata("image/png", MAX_BYTES + 1);

        // When & Then
        assertThatThrownBy(() -> policy.validate(image))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }
}