package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.exception.UnauthorizedException;
import com.chalkak.backend.user.domain.SignatureImagePolicy;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.repository.SignatureImageStorage;
import com.chalkak.backend.user.repository.SignatureImageUpload;
import com.chalkak.backend.user.repository.SignatureImageUploadIssuer;
import com.chalkak.backend.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialSignupService {

    private final SocialIdentityVerifier socialIdentityVerifier;
    private final SocialAccountRepository socialAccountRepository;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImagePolicy signatureImagePolicy;
    private final UserRepository userRepository;

    public SignatureImageUpload createSignatureUpload(
            SocialProvider provider,
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);
        validateNewSocialAccount(identity);

        return signatureImageUploadIssuer.issue(UUID.randomUUID());
    }

    @Transactional
    public UUID signup(
            SocialProvider provider,
            String idToken,
            UUID signatureOriginalUploadId
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);
        Optional<SocialAccount> existingSocialAccount = socialAccountRepository
                .findByProviderAndSubject(
                        identity.provider(),
                        identity.subject());
        if (existingSocialAccount.isPresent()) {
            return getExistingUserId(existingSocialAccount.get());
        }

        SignatureStorageKeys storageKeys = signatureImageStorage
                .toStorageKeys(signatureOriginalUploadId);
        validateUnusedSignature(storageKeys);

        StoredImageMetadata image = signatureImageStorage
                .findUploadedImage(signatureOriginalUploadId)
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "업로드한 사인 이미지를 찾을 수 없습니다."));
        if (!signatureImageStorage.isProcessingCompleted(
                signatureOriginalUploadId)) {
            // TODO: HTTP 예외 컨벤션이 확장되면 409 Conflict로 매핑할지 검토한다.
            throw new BusinessException(
                    ErrorCode.SIGNATURE_PROCESSING_PENDING,
                    "사인 이미지 처리 중입니다.");
        }
        signatureImagePolicy.validate(image);

        User user = userRepository.save(User.create(identity.email(), storageKeys));
        socialAccountRepository.save(SocialAccount.create(
                user,
                identity.provider(),
                identity.subject()));

        return user.getId();
    }

    private UUID getExistingUserId(SocialAccount socialAccount) {
        if (socialAccount.getUser().isDeleted()) {
            throw new UnauthorizedException(
                    ErrorCode.UNAUTHORIZED,
                    "탈퇴한 회원은 회원가입할 수 없습니다.");
        }
        return socialAccount.getUser().getId();
    }

    private void validateUnusedSignature(SignatureStorageKeys storageKeys) {
        if (userRepository.existsBySignatureOriginalStorageKey(
                storageKeys.originalStorageKey())) {
            throw new NotFoundException(
                    ErrorCode.BUSINESS_ERROR,
                    "업로드한 사인 이미지를 찾을 수 없습니다.");
        }
    }

    private void validateNewSocialAccount(VerifiedSocialIdentity identity) {
        boolean exists = socialAccountRepository.findByProviderAndSubject(
                        identity.provider(),
                        identity.subject())
                .isPresent();
        if (exists) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 가입된 소셜 계정입니다.");
        }
    }
}
