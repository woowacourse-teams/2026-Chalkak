package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.BDDMockito.given;

import com.chalkak.backend.IntegrationTestSupport;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class UserServiceTest extends IntegrationTestSupport {

    private static final StoredImageMetadata VALID_IMAGE = new StoredImageMetadata("image/png", 1024L);

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SignatureImageStorage signatureImageStorage;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("탈퇴하면 개인 식별 정보가 비식별화되고 탈퇴 시각이 기록된다")
    void withdraw_activeUser_anonymizesPersonalData() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id);
        flushAndClear();

        // Then
        User withdrawn = userRepository.findById(id).orElseThrow();
        assertThat(withdrawn.getEmail()).isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(withdrawn.getSignatureOriginalStorageKey()).isEqualTo("withdrawn/" + id);
        assertThat(withdrawn.getSignatureThumbnailStorageKey()).isNull();
        assertThat(withdrawn.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴한 회원은 살아있는 회원 조회에서 제외된다")
    void withdraw_activeUser_excludesFromActiveQuery() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        // When
        userService.withdraw(id);
        flushAndClear();

        // Then
        assertThat(userRepository.findActiveById(id)).isEmpty();
        assertThat(userRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("존재하지 않는 회원의 탈퇴는 거부한다")
    void withdraw_notExistingUser_throwsNotFoundException() {
        // Given
        UUID notExistingId = UUID.randomUUID();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(notExistingId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("탈퇴할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 존재하지 않는 회원과 동일하게 거부한다")
    void withdraw_alreadyWithdrawnUser_throwsNotFoundException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(id);
        flushAndClear();

        // When & Then
        assertThatThrownBy(() -> userService.withdraw(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("탈퇴할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("사인을 교체하면 최종 경로가 저장되고 이미지 URL을 반환한다")
    void updateSignature_uploadedImage_replacesSignature() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();
        UUID uploadId = UUID.randomUUID();
        String storageKey = "chalkak/signatures/original/" + uploadId + ".png";
        given(signatureImageStorage.toOriginalStorageKey(uploadId)).willReturn(storageKey);
        given(signatureImageStorage.findUploadedImage(uploadId)).willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(storageKey))
                .willReturn("https://cdn.test.chalkak/signatures/original/" + uploadId + ".png");

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();
        assertThat(updated.getSignatureOriginalStorageKey()).isEqualTo(storageKey);
        assertThat(updated.getSignatureThumbnailStorageKey()).isNull();
        assertThat(imageUrl)
                .isEqualTo("https://cdn.test.chalkak/signatures/original/" + uploadId + ".png");
    }

    @Test
    @DisplayName("다른 회원이 이미 쓴 사인 키는 사용할 수 없다")
    void updateSignature_alreadyUsedStorageKey_throwsNotFoundException() {
        // Given
        User owner = userRepository.save(UserFixture.create());
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toOriginalStorageKey(uploadId))
                .willReturn(owner.getSignatureOriginalStorageKey());

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("업로드되지 않은 사인 이미지는 사용할 수 없다")
    void updateSignature_notUploadedImage_throwsNotFoundException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toOriginalStorageKey(uploadId))
                .willReturn("chalkak/signatures/original/" + uploadId + ".png");
        given(signatureImageStorage.findUploadedImage(uploadId)).willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("업로드한 사인 이미지를 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("허용하지 않는 형식의 사인 이미지는 사용할 수 없다")
    void updateSignature_disallowedContentType_throwsBusinessException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toOriginalStorageKey(uploadId))
                .willReturn("chalkak/signatures/original/" + uploadId + ".png");
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(new StoredImageMetadata("image/jpeg", 1024L)));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사용할 수 없는 사인 이미지입니다.");
    }

    @Test
    @DisplayName("존재하지 않는 회원의 사인 교체는 거부한다")
    void updateSignature_notExistingUser_throwsNotFoundException() {
        // Given
        UUID notExistingId = UUID.randomUUID();
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toOriginalStorageKey(uploadId))
                .willReturn("chalkak/signatures/original/" + uploadId + ".png");
        given(signatureImageStorage.findUploadedImage(uploadId)).willReturn(Optional.of(VALID_IMAGE));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(notExistingId, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사인을 교체할 회원을 찾을 수 없습니다.");
    }

    @Test
    @DisplayName("탈퇴한 회원의 사인 교체는 거부한다")
    void updateSignature_withdrawnUser_throwsNotFoundException() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        userService.withdraw(id);
        flushAndClear();
        UUID uploadId = UUID.randomUUID();
        given(signatureImageStorage.toOriginalStorageKey(uploadId))
                .willReturn("chalkak/signatures/original/" + uploadId + ".png");
        given(signatureImageStorage.findUploadedImage(uploadId)).willReturn(Optional.of(VALID_IMAGE));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사인을 교체할 회원을 찾을 수 없습니다.");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
