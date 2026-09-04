package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.SocialAccountRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import com.chalkak.backend.exception.ForbiddenException;
import com.chalkak.backend.exception.NotFoundException;
import com.chalkak.backend.user.domain.SignatureImagePolicy;
import com.chalkak.backend.user.domain.SignatureStorageKeys;
import com.chalkak.backend.user.domain.StoredImageMetadata;
import com.chalkak.backend.user.domain.User;
import com.chalkak.backend.user.domain.UserStatus;
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
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;
    private final SocialSignupTokenIssuer socialSignupTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SocialSignupTokenVerifier socialSignupTokenVerifier;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImagePolicy signatureImagePolicy;
    private final UserRepository userRepository;
    private final UserRefreshTokenService userRefreshTokenService;

    public SocialSignupSignatureUploadResult createSignatureUpload(
            SocialProvider provider,
            String idToken
    ) {
        VerifiedSocialIdentity identity = socialIdentityVerifier.verify(
                provider,
                idToken);
        validateNewSocialAccount(identity);

        UUID uploadId = UUID.randomUUID();
        SignatureImageUpload upload = signatureImageUploadIssuer.issue(uploadId);
        IssuedSocialSignupToken signupToken = socialSignupTokenIssuer.issue(
                identity,
                uploadId);

        return new SocialSignupSignatureUploadResult(upload, signupToken);
    }

    @Transactional
    public SocialSignupResult signup(String signupToken) {
        VerifiedSocialSignupToken verifiedToken =
                socialSignupTokenVerifier.verify(signupToken);
        String subjectHmac = fingerprintEncoder.encode(
                verifiedToken.provider(),
                verifiedToken.subject());
        Optional<SocialAccount> existingSocialAccount = socialAccountRepository
                .findByProviderAndSubjectHmac(
                        verifiedToken.provider(),
                        subjectHmac);
        if (existingSocialAccount.isPresent()) {
            return toSignupResult(getExistingUser(existingSocialAccount.get()));
        }

        SignatureStorageKeys storageKeys = signatureImageStorage
                .toStorageKeys(verifiedToken.uploadId());
        validateUnusedSignature(storageKeys);

        StoredImageMetadata image = signatureImageStorage
                .findUploadedImage(verifiedToken.uploadId())
                .orElseThrow(() -> new NotFoundException(
                        ErrorCode.BUSINESS_ERROR,
                        "업로드한 사인 이미지를 찾을 수 없습니다."));
        if (!signatureImageStorage.isProcessingCompleted(
                verifiedToken.uploadId())) {
            // TODO: HTTP 예외 컨벤션이 확장되면 409 Conflict로 매핑할지 검토한다.
            throw new BusinessException(
                    ErrorCode.SIGNATURE_PROCESSING_PENDING,
                    "사인 이미지 처리 중입니다.");
        }
        signatureImagePolicy.validate(image);

        User user = userRepository.save(User.create(
                verifiedToken.email(),
                storageKeys));
        socialAccountRepository.save(SocialAccount.create(
                user,
                verifiedToken.provider(),
                subjectHmac));

        return toSignupResult(user);
    }

    /**
     * 리프레시 토큰 계보는 회원 엔티티에 FK로 매달리므로 식별자만으로는 만들 수 없다. 가입 직후
     * 바로 재발급할 수 있도록 액세스 토큰과 같은 자리에서 함께 발급한다.
     */
    private SocialSignupResult toSignupResult(User user) {
        return new SocialSignupResult(
                user.getId(),
                accessTokenIssuer.issue(user.getId()),
                userRefreshTokenService.issue(user));
    }

    private User getExistingUser(SocialAccount socialAccount) {
        User user = socialAccount.getUser();
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "차단된 소셜 계정입니다.");
        }
        if (user.isDeleted()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 가입된 소셜 계정입니다.");
        }
        return user;
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
        String subjectHmac = fingerprintEncoder.encode(
                identity.provider(),
                identity.subject());
        socialAccountRepository.findByProviderAndSubjectHmac(
                        identity.provider(),
                        subjectHmac)
                .ifPresent(this::validateAvailableForSignup);
    }

    private void validateAvailableForSignup(SocialAccount socialAccount) {
        User user = socialAccount.getUser();
        if (user.getStatus() == UserStatus.BANNED) {
            throw new ForbiddenException(
                    ErrorCode.FORBIDDEN,
                    "차단된 소셜 계정입니다.");
        }
        throw new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "이미 가입된 소셜 계정입니다.");
    }
}
