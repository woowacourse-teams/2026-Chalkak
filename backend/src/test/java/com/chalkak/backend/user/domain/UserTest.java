package com.chalkak.backend.user.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chalkak.backend.exception.BusinessException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    @DisplayName("이메일 없이 처리 완료된 사인으로 활성 회원을 생성한다")
    void create_withoutEmail_createsActiveUserWithSignature() {
        // Given
        SignatureStorageKeys storageKeys = new SignatureStorageKeys(
                "chalkak/signatures/dev/original/signature.png",
                "chalkak/signatures/dev/thumbnail/signature.png");

        // When
        User user = User.create(null, storageKeys);

        // Then
        assertThat(user.getEmail()).isNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo(storageKeys.originalStorageKey());
        assertThat(user.getSignatureThumbnailStorageKey()).isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(user.getPendingSignatureUploadId()).isNull();
        assertThat(user.getSignatureProcessingStatus()).isNull();
        assertThat(user.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("원본 사인 키가 없으면 회원을 생성할 수 없다")
    void create_missingOriginalStorageKey_throwsException() {
        // Given
        SignatureStorageKeys storageKeys = new SignatureStorageKeys(
                null,
                "chalkak/signatures/dev/thumbnail/signature.png");

        // When & Then
        assertThatThrownBy(() -> User.create("user@chalkak.test", storageKeys))
                .isInstanceOf(BusinessException.class)
                .hasMessage("사인 이미지 업로드 정보가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("탈퇴하면 개인 식별 정보가 비식별화된다")
    void withdraw_activeUser_anonymizesPersonalData() {
        // Given
        UUID id = UUID.randomUUID();
        User user = UserFixture.create(id);

        // When
        user.withdraw();

        // Then
        assertThat(user.getEmail()).isEqualTo("withdrawn+" + id + "@chalkak.invalid");
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo("withdrawn/" + id);
        assertThat(user.getSignatureThumbnailStorageKey()).isNull();
        assertThat(user.getPendingSignatureUploadId()).isNull();
        assertThat(user.getSignatureProcessingStatus()).isNull();
        assertThat(user.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("탈퇴하면 탈퇴 시각이 기록된다")
    void withdraw_activeUser_recordsDeletedAt() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When
        user.withdraw();

        // Then
        assertThat(user.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("이미 탈퇴한 회원은 다시 탈퇴할 수 없다")
    void withdraw_alreadyWithdrawnUser_throwsException() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThatThrownBy(user::withdraw)
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 탈퇴한 회원입니다.");
    }

    @Test
    @DisplayName("탈퇴 전에는 삭제된 회원이 아니다")
    void isDeleted_activeUser_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("탈퇴하지 않고 상태가 ACTIVE면 활성 회원이다")
    void isActive_activeUser_returnsTrue() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.isActive()).isTrue();
    }

    @Test
    @DisplayName("차단된 회원은 활성 회원이 아니다")
    void isActive_bannedUser_returnsFalse() {
        // Given
        User user = UserFixture.createBanned(UUID.randomUUID());

        // When & Then
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("탈퇴한 회원은 활성 회원이 아니다")
    void isActive_withdrawnUser_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThat(user.isActive()).isFalse();
    }

    @Test
    @DisplayName("사인 처리를 시작해도 현재 활성 사인은 유지한다")
    void startSignatureProcessing_activeSignature_preservesActiveKeys() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        String originalStorageKey = user.getSignatureOriginalStorageKey();
        String thumbnailStorageKey = user.getSignatureThumbnailStorageKey();
        Instant startedAt = Instant.parse("2026-08-24T10:00:00Z");

        // When
        user.startSignatureProcessing(uploadId, startedAt);

        // Then
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo(originalStorageKey);
        assertThat(user.getSignatureThumbnailStorageKey()).isEqualTo(thumbnailStorageKey);
        assertThat(user.getPendingSignatureUploadId()).isEqualTo(uploadId);
        assertThat(user.getSignatureProcessingStatus()).isEqualTo(SignatureProcessingStatus.PROCESSING);
        assertThat(user.getSignatureProcessingStartedAt()).isEqualTo(startedAt);
    }

    @Test
    @DisplayName("처리 중인 업로드가 완료되면 활성 키를 승격하고 pending 정보를 지운다")
    void completeSignatureProcessing_matchingUpload_promotesAndClearsPending() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        SignatureStorageKeys storageKeys = new SignatureStorageKeys(
                "chalkak/signatures/dev/original/" + uploadId + ".png",
                "chalkak/signatures/dev/thumbnail/" + uploadId + ".png");
        user.startSignatureProcessing(uploadId, Instant.now());

        // When
        boolean completed = user.completeSignatureProcessing(uploadId, storageKeys);

        // Then
        assertThat(completed).isTrue();
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo(storageKeys.originalStorageKey());
        assertThat(user.getSignatureThumbnailStorageKey()).isEqualTo(storageKeys.thumbnailStorageKey());
        assertThat(user.getPendingSignatureUploadId()).isNull();
        assertThat(user.getSignatureProcessingStatus()).isNull();
        assertThat(user.getSignatureProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("현재 pending과 다른 업로드의 느린 완료 콜백은 무시한다")
    void completeSignatureProcessing_staleUpload_ignoresCallback() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID pendingUploadId = UUID.randomUUID();
        String originalStorageKey = user.getSignatureOriginalStorageKey();
        user.startSignatureProcessing(pendingUploadId, Instant.now());

        SignatureStorageKeys staleStorageKeys = new SignatureStorageKeys(
                "chalkak/signatures/dev/original/stale.png",
                "chalkak/signatures/dev/thumbnail/stale.png");

        // When
        boolean completed = user.completeSignatureProcessing(
                UUID.randomUUID(),
                staleStorageKeys);

        // Then
        assertThat(completed).isFalse();
        assertThat(user.getSignatureOriginalStorageKey())
                .isEqualTo(originalStorageKey);
        assertThat(user.getPendingSignatureUploadId())
                .isEqualTo(pendingUploadId);
    }

    @Test
    @DisplayName("현재 사인과 같은 원본 키를 가지고 있으면 참이다")
    void hasSignature_sameStorageKey_returnsTrue() {
        // Given
        UUID id = UUID.randomUUID();
        User user = UserFixture.create(id);

        // When & Then
        assertThat(user.hasSignature("signatures/" + id)).isTrue();
    }

    @Test
    @DisplayName("현재 사인과 다른 원본 키를 가지고 있으면 거짓이다")
    void hasSignature_differentStorageKey_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.hasSignature(
                "chalkak/signatures/dev/original/"
                        + UUID.randomUUID()
                        + ".png"))
                .isFalse();
    }

    @Test
    @DisplayName("null 원본 키를 조회하면 거짓이다")
    void hasSignature_nullStorageKey_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When & Then
        assertThat(user.hasSignature(null)).isFalse();
    }

    @Test
    @DisplayName("처리 중인 동일 업로드 ID는 처리 중으로 판단한다")
    void isSignatureProcessing_processingUpload_returnsTrue() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        user.startSignatureProcessing(uploadId, Instant.now());

        // When & Then
        assertThat(user.isSignatureProcessing(uploadId)).isTrue();
    }

    @Test
    @DisplayName("실패한 동일 업로드 ID는 처리 중이 아니라 실패로 판단한다")
    void isSignatureProcessing_failedUpload_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        user.startSignatureProcessing(uploadId, Instant.now());
        user.failSignatureProcessing(uploadId);

        // When & Then
        assertThat(user.isSignatureProcessing(uploadId)).isFalse();
        assertThat(user.isSignatureProcessingFailed(uploadId)).isTrue();
    }

    @Test
    @DisplayName("현재 pending과 다른 업로드 ID이면 처리 중이 아니다")
    void isSignatureProcessing_differentUpload_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.startSignatureProcessing(UUID.randomUUID(), Instant.now());

        // When & Then
        assertThat(user.isSignatureProcessing(UUID.randomUUID())).isFalse();
    }

    @Test
    @DisplayName("영구 실패 콜백은 기존 활성 사인을 유지하고 pending 상태를 실패로 바꾼다")
    void failSignatureProcessing_matchingUpload_marksFailedAndPreservesActiveKeys() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        String originalStorageKey = user.getSignatureOriginalStorageKey();
        user.startSignatureProcessing(uploadId, Instant.now());

        // When
        boolean failed = user.failSignatureProcessing(uploadId);

        // Then
        assertThat(failed).isTrue();
        assertThat(user.getSignatureOriginalStorageKey()).isEqualTo(originalStorageKey);
        assertThat(user.getPendingSignatureUploadId()).isEqualTo(uploadId);
        assertThat(user.getSignatureProcessingStatus()).isEqualTo(SignatureProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("사인 처리 시작 후 정확히 제한 시간이 지나면 실패로 바꾼다")
    void failSignatureProcessingIfTimedOut_atTimeout_marksFailed() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        UUID uploadId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-08-26T00:00:00Z");
        Duration timeout = Duration.ofMinutes(15);
        user.startSignatureProcessing(uploadId, startedAt);

        // When
        boolean failed = user.failSignatureProcessingIfTimedOut(
                startedAt.plus(timeout),
                timeout);

        // Then
        assertThat(failed).isTrue();
        assertThat(user.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("사인 처리 제한 시간 직전에는 처리 중 상태를 유지한다")
    void failSignatureProcessingIfTimedOut_beforeTimeout_keepsProcessing() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        Instant startedAt = Instant.parse("2026-08-26T00:00:00Z");
        Duration timeout = Duration.ofMinutes(15);
        user.startSignatureProcessing(UUID.randomUUID(), startedAt);

        // When
        boolean failed = user.failSignatureProcessingIfTimedOut(
                startedAt.plus(timeout).minusNanos(1),
                timeout);

        // Then
        assertThat(failed).isFalse();
        assertThat(user.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.PROCESSING);
    }

    @Test
    @DisplayName("사인 처리 제한 시간을 초과하면 실패로 바꾼다")
    void failSignatureProcessingIfTimedOut_afterTimeout_marksFailed() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        Instant startedAt = Instant.parse("2026-08-26T00:00:00Z");
        Duration timeout = Duration.ofMinutes(15);
        user.startSignatureProcessing(UUID.randomUUID(), startedAt);

        // When
        boolean failed = user.failSignatureProcessingIfTimedOut(
                startedAt.plus(timeout).plusNanos(1),
                timeout);

        // Then
        assertThat(failed).isTrue();
        assertThat(user.getSignatureProcessingStatus())
                .isEqualTo(SignatureProcessingStatus.FAILED);
    }

    @Test
    @DisplayName("처리 중인 사인이 없으면 타임아웃 상태를 변경하지 않는다")
    void failSignatureProcessingIfTimedOut_withoutPending_returnsFalse() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());

        // When
        boolean failed = user.failSignatureProcessingIfTimedOut(
                Instant.parse("2026-08-26T00:15:00Z"),
                Duration.ofMinutes(15));

        // Then
        assertThat(failed).isFalse();
        assertThat(user.getSignatureProcessingStatus()).isNull();
    }

    @Test
    @DisplayName("탈퇴한 회원은 사인 처리를 시작할 수 없다")
    void startSignatureProcessing_withdrawnUser_throwsException() {
        // Given
        User user = UserFixture.create(UUID.randomUUID());
        user.withdraw();

        // When & Then
        assertThatThrownBy(() -> user.startSignatureProcessing(UUID.randomUUID(), Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("이미 탈퇴한 회원입니다.");
    }
}
