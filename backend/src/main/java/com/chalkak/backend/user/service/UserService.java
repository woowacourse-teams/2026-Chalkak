package com.chalkak.backend.user.service;

import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.SignatureImagePolicy;
import com.chalkak.backend.user.domain.SignatureProcessingStatus;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final Duration SIGNATURE_PROCESSING_TIMEOUT = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;
    private final SignatureImagePolicy signatureImagePolicy;

    public SignatureImageUpload createSignatureUpload(UUID userId) {
        getActiveUser(userId, "사인을 업로드할 회원을 찾을 수 없습니다.");

        return signatureImageUploadIssuer.issue(UUID.randomUUID());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public String getSignature(UUID userId) {
        User user = getActiveUserForUpdate(
                userId,
                "사인을 조회할 회원을 찾을 수 없습니다.");

        failTimedOutSignatureProcessing(user);
        validateSignatureProcessingNotFailed(user);

        return getSignatureImageUrl(user);
    }

    @Transactional
    public void withdraw(UUID userId) {
        User user = getActiveUser(userId, "탈퇴할 회원을 찾을 수 없습니다.");

        user.withdraw();
    }

    @Transactional
    public String updateSignature(UUID userId, UUID uploadId) {
        SignatureStorageKeys storageKeys =
                signatureImageStorage.toStorageKeys(uploadId);

        User user = getActiveUserForUpdate(
                userId,
                "사인을 교체할 회원을 찾을 수 없습니다.");

        // 이미 활성인 사인을 다시 고른 것이므로, 진행 중이던 다른 작업은 사용자가 되돌린 것으로 본다.
        // 취소하지 않으면 그 작업의 완료 콜백이 나중에 도착해 사용자의 최신 선택을 덮어쓴다.
        if (user.hasSignature(storageKeys.originalStorageKey())) {
            user.cancelSignatureProcessing();
            return getSignatureImageUrl(user);
        }

        validateSignatureProcessingNotFailed(user, uploadId);

        // 동일한 비동기 작업의 재요청이면 처리 시각과 상태를 초기화하지 않는다.
        if (user.isSignatureProcessing(uploadId)) {
            return getSignatureImageUrl(user);
        }

        validateSignatureImage(uploadId, storageKeys);

        user.startSignatureProcessing(uploadId, Instant.now());
        completeSignatureProcessingIfReady(user, uploadId, storageKeys);

        return getSignatureImageUrl(user);
    }

    @Transactional
    public void completeSignatureProcessing(UUID uploadId) {
        userRepository.findActiveByPendingSignatureUploadIdForUpdate(uploadId)
                .ifPresent(user -> user.completeSignatureProcessing(
                        uploadId,
                        signatureImageStorage.toStorageKeys(uploadId)));
    }

    @Transactional
    public void failSignatureProcessing(UUID uploadId) {
        userRepository.findActiveByPendingSignatureUploadIdForUpdate(uploadId)
                .ifPresent(user -> user.failSignatureProcessing(uploadId));
    }

    private User getActiveUser(UUID userId, String notFoundMessage) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        notFoundMessage));
    }

    private User getActiveUserForUpdate(UUID userId, String notFoundMessage) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        notFoundMessage));
    }

    private void failTimedOutSignatureProcessing(User user) {
        user.failSignatureProcessingIfTimedOut(
                Instant.now(),
                SIGNATURE_PROCESSING_TIMEOUT);
    }

    private void validateSignatureProcessingNotFailed(User user) {
        if (user.getSignatureProcessingStatus() != SignatureProcessingStatus.FAILED) {
            return;
        }

        throw new BusinessException(
                ErrorCode.SIGNATURE_PROCESSING_FAILED,
                "사인 이미지 처리에 실패했습니다. 새 이미지를 등록해 주세요.");
    }

    private void validateSignatureProcessingNotFailed(User user, UUID uploadId) {
        // 영구 실패한 업로드는 같은 바이트를 다시 처리해도 실패하므로 재등록만이 탈출구다.
        if (!user.isSignatureProcessingFailed(uploadId)) {
            return;
        }

        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "처리할 수 없는 사인 이미지입니다. 새 이미지를 업로드해 주세요.");
    }

    private void validateSignatureImage(
            UUID uploadId,
            SignatureStorageKeys storageKeys
    ) {
        if (userRepository.existsBySignatureOriginalStorageKey(
                storageKeys.originalStorageKey())) {
            throw new NotFoundException(
                    ErrorCode.BUSINESS_ERROR,
                    "업로드한 사인 이미지를 찾을 수 없습니다.");
        }

        StoredImageMetadata image = signatureImageStorage.findUploadedImage(uploadId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "업로드한 사인 이미지를 찾을 수 없습니다."));
        signatureImagePolicy.validate(image);
    }

    private void completeSignatureProcessingIfReady(
            User user,
            UUID uploadId,
            SignatureStorageKeys storageKeys
    ) {
        // Lambda가 등록 API보다 먼저 완료된 경우 즉시 승격한다.
        if (!signatureImageStorage.isProcessingCompleted(uploadId)) {
            return;
        }

        user.completeSignatureProcessing(uploadId, storageKeys);
    }

    private String getSignatureImageUrl(User user) {
        return signatureImageStorage.toImageUrl(
                user.getSignatureOriginalStorageKey());
    }
}
