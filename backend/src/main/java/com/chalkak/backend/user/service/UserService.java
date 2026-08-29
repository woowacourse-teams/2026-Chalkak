package com.chalkak.backend.user.service;

import com.chalkak.backend.auth.repository.SocialAccountRepository;
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
import com.chalkak.backend.user.repository.SignatureProcessingImageUpload;
import com.chalkak.backend.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;
    private final SignatureImagePolicy signatureImagePolicy;
    private final SignatureProcessingPolicy signatureProcessingPolicy;

    public SignatureImageUpload createSignatureUpload(UUID userId) {
        getActiveUser(userId, "사인을 업로드할 회원을 찾을 수 없습니다.");

        return signatureImageUploadIssuer.issue(UUID.randomUUID());
    }

    public SignatureProcessingImageUpload issueSignatureProcessingUpload(UUID uploadId) {
        return signatureImageUploadIssuer.issueProcessingUpload(uploadId);
    }

    public UserSignatureResult getSignature(UUID userId) {
        User user = getActiveUser(
                userId,
                "사인을 조회할 회원을 찾을 수 없습니다.");

        if (user.getSignatureProcessingStatus() == SignatureProcessingStatus.FAILED
                || isSignatureProcessingTimedOut(user, Instant.now())) {
            throw new BusinessException(
                    ErrorCode.SIGNATURE_REGISTRATION_REQUIRED,
                    "사인 이미지 처리에 실패했습니다. 사인을 다시 등록해 주세요.");
        }

        return new UserSignatureResult(
                signatureImageStorage.toImageUrl(user.getSignatureOriginalStorageKey()),
                signatureImageStorage.toImageUrl(user.getSignatureThumbnailStorageKey()));
    }

    @Transactional
    public String updateSignature(UUID userId, UUID uploadId) {
        SignatureStorageKeys storageKeys =
                signatureImageStorage.toStorageKeys(uploadId);

        User user = userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "사인을 교체할 회원을 찾을 수 없습니다."));

        if (user.hasSignature(storageKeys.originalStorageKey())) {
            user.cancelSignatureProcessing();
            return getSignatureImageUrl(user);
        }

        if (user.isSignatureProcessingFailed(uploadId)
                || (user.isSignatureProcessing(uploadId)
                && isSignatureProcessingTimedOut(user, Instant.now()))) {
            throw new BusinessException(
                    ErrorCode.SIGNATURE_REUPLOAD_REQUIRED,
                    "사인 이미지 처리에 실패했습니다. "
                            + "새로운 업로드 ID를 발급받아 이미지를 다시 업로드해 주세요.");
        }

        if (user.isSignatureProcessing(uploadId)) {
            return getSignatureImageUrl(user);
        }

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

        user.startSignatureProcessing(uploadId, Instant.now());
        if (signatureImageStorage.isProcessingCompleted(uploadId)) {
            user.completeSignatureProcessing(uploadId, storageKeys);
        }

        return getSignatureImageUrl(user);
    }

    @Transactional
    public void completeSignatureProcessing(UUID uploadId) {
        Instant currentTime = Instant.now();

        userRepository.findActiveByPendingSignatureUploadIdForUpdate(uploadId)
                .ifPresent(user -> {
                    if (!user.isSignatureProcessing(uploadId)) {
                        return;
                    }
                    if (isSignatureProcessingTimedOut(user, currentTime)) {
                        return;
                    }

                    user.completeSignatureProcessing(
                            uploadId,
                            signatureImageStorage.toStorageKeys(uploadId));
                });
    }

    @Transactional
    public void failSignatureProcessing(UUID uploadId) {
        userRepository.findActiveByPendingSignatureUploadIdForUpdate(uploadId)
                .ifPresent(user -> user.failSignatureProcessing(uploadId));
    }

    @Transactional
    public void withdraw(UUID userId) {
        User user = getActiveUser(userId, "탈퇴할 회원을 찾을 수 없습니다.");

        socialAccountRepository.deleteByUserId(userId);
        user.withdraw();
    }

    private User getActiveUser(UUID userId, String notFoundMessage) {
        return userRepository.findActiveById(userId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        notFoundMessage));
    }

    private boolean isSignatureProcessingTimedOut(
            User user,
            Instant currentTime
    ) {
        if (user.getSignatureProcessingStatus() != SignatureProcessingStatus.PROCESSING) {
            return false;
        }

        return signatureProcessingPolicy.isProcessingTimedOut(
                user.getSignatureProcessingStartedAt(),
                currentTime);
    }

    private String getSignatureImageUrl(User user) {
        return signatureImageStorage.toImageUrl(
                user.getSignatureOriginalStorageKey());
    }
}
