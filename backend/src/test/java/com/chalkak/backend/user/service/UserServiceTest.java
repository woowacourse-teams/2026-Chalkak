package com.chalkak.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.support.IntegrationTestSupport;
import com.chalkak.backend.user.domain.SignatureProcessingStatus;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserFixture;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class UserServiceTest extends IntegrationTestSupport {

    private static final StoredImageMetadata VALID_IMAGE =
            new StoredImageMetadata("image/png", 1024L);

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

        assertThat(withdrawn.getEmail())
                .isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(withdrawn.getSignatureOriginalStorageKey())
                .isEqualTo("withdrawn/" + id);
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
    @DisplayName("사인 수정을 요청하면 기존 활성 사인을 유지하고 pending 정보를 저장한다")
    void updateSignature_uploadedImage_startsProcessingAndPreservesActiveSignature() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
        assertThat(imageUrl).isEqualTo(activeImageUrl);
    }

    @Test
    @DisplayName("Lambda가 등록 API보다 먼저 완료했으면 pending 저장 후 즉시 승격한다")
    void updateSignature_alreadyProcessedImage_promotesImmediately() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String completedImageUrl =
                "https://cdn.test.chalkak/signatures/dev/original/"
                        + uploadId
                        + ".png";

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.isProcessingCompleted(uploadId))
                .willReturn(true);
        given(signatureImageStorage.toImageUrl(
                storageKeys.originalStorageKey()))
                .willReturn(completedImageUrl);

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
        assertThat(updated.getSignatureProcessingStartedAt()).isNull();
        assertThat(imageUrl).isEqualTo(completedImageUrl);
    }

    @Test
    @DisplayName("성공 콜백은 pending 사인을 활성 사인으로 승격한다")
    void completeSignatureProcessing_matchingPending_promotesSignature() {
        // Given
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));

        userService.updateSignature(id, uploadId);
        flushAndClear();

        // When
        userService.completeSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(storageKeys.originalStorageKey());
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(updated.getPendingSignatureUploadId()).isNull();
        assertThat(updated.getSignatureProcessingStatus()).isNull();
        assertThat(updated.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("실패 콜백은 기존 활성 사인을 유지하고 처리 상태만 실패로 바꾼다")
    void failSignatureProcessing_matchingPending_preservesActiveSignature() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));

        userService.updateSignature(id, uploadId);
        flushAndClear();

        // When
        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
        assertThat(updated.getSignatureProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("현재 pending과 다른 업로드의 느린 성공 콜백은 무시한다")
    void completeSignatureProcessing_staleUpload_ignoresCallback() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        // When
        userService.completeSignatureProcessing(UUID.randomUUID());
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
    }

    @Test
    @DisplayName("처리 중인 같은 사인 업로드를 다시 요청해도 같은 활성 이미지 URL을 반환한다")
    void updateSignature_samePendingUploadIdTwice_returnsSameActiveImageUrl() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String activeThumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        // When
        String firstImageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        Instant firstStartedAt = userRepository.findById(id)
                .orElseThrow()
                .getSignatureProcessingStartedAt();

        String secondImageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(firstImageUrl).isEqualTo(activeImageUrl);
        assertThat(secondImageUrl).isEqualTo(activeImageUrl);
        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(activeThumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(updated.getSignatureProcessingStartedAt())
                .isEqualTo(firstStartedAt);
    }

    @Test
    @DisplayName("실패한 같은 사인 업로드를 다시 요청해도 실패 상태를 초기화하지 않는다")
    void updateSignature_sameFailedUploadId_preservesFailedState() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String activeOriginalStorageKey =
                saved.getSignatureOriginalStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = storageKeys(uploadId);
        String activeImageUrl =
                "https://cdn.test.chalkak/" + activeOriginalStorageKey;

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys);
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(VALID_IMAGE));
        given(signatureImageStorage.toImageUrl(activeOriginalStorageKey))
                .willReturn(activeImageUrl);

        userService.updateSignature(id, uploadId);
        flushAndClear();

        userService.failSignatureProcessing(uploadId);
        flushAndClear();

        Instant startedAt = userRepository.findById(id)
                .orElseThrow()
                .getSignatureProcessingStartedAt();

        // When
        String imageUrl = userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(imageUrl).isEqualTo(activeImageUrl);
        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(activeOriginalStorageKey);
        assertThat(updated.getPendingSignatureUploadId())
                .isEqualTo(uploadId);
        assertThat(updated.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
        assertThat(updated.getSignatureProcessingStartedAt())
                .isEqualTo(startedAt);
    }

    @Test
    @DisplayName("이미 반영된 사인을 다시 요청해도 썸네일 키를 유지한다")
    void updateSignature_storageKeyAlreadyOwnedByUser_keepsThumbnailKey() {
        // Given
        User saved = userRepository.save(UserFixture.create());
        UUID id = saved.getId();
        String originalStorageKey =
                saved.getSignatureOriginalStorageKey();
        String thumbnailStorageKey =
                saved.getSignatureThumbnailStorageKey();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        originalStorageKey,
                        "chalkak/signatures/dev/thumbnail/"
                                + uploadId
                                + ".png"));
        given(signatureImageStorage.toImageUrl(originalStorageKey))
                .willReturn(
                        "https://cdn.test.chalkak/" + originalStorageKey);

        // When
        userService.updateSignature(id, uploadId);
        flushAndClear();

        // Then
        User updated = userRepository.findById(id).orElseThrow();

        assertThat(updated.getSignatureOriginalStorageKey())
                .isEqualTo(originalStorageKey);
        assertThat(updated.getSignatureThumbnailStorageKey())
                .isEqualTo(thumbnailStorageKey);
        assertThat(updated.getPendingSignatureUploadId()).isNull();
    }

    @Test
    @DisplayName("다른 회원이 이미 쓴 사인 키는 사용할 수 없다")
    void updateSignature_alreadyUsedStorageKey_throwsNotFoundException() {
        // Given
        User owner = userRepository.save(UserFixture.create());
        UUID id = userRepository.save(UserFixture.create()).getId();
        flushAndClear();

        UUID uploadId = UUID.randomUUID();

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(new SignatureStorageKeys(
                        owner.getSignatureOriginalStorageKey(),
                        "chalkak/signatures/dev/thumbnail/"
                                + uploadId
                                + ".png"));

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

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.empty());

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

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));
        given(signatureImageStorage.findUploadedImage(uploadId))
                .willReturn(Optional.of(
                        new StoredImageMetadata("image/jpeg", 1024L)));

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

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When & Then
        assertThatThrownBy(() ->
                userService.updateSignature(notExistingId, uploadId))
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

        given(signatureImageStorage.toStorageKeys(uploadId))
                .willReturn(storageKeys(uploadId));

        // When & Then
        assertThatThrownBy(() -> userService.updateSignature(id, uploadId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("사인을 교체할 회원을 찾을 수 없습니다.");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private SignatureStorageKeys storageKeys(UUID uploadId) {
        return new SignatureStorageKeys(
                "chalkak/signatures/dev/original/"
                        + uploadId
                        + ".png",
                "chalkak/signatures/dev/thumbnail/"
                        + uploadId
                        + ".png");
    }
}