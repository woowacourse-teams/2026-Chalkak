package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.ConsumedSignupToken;
import com.chalkak.backend.auth.domain.IssuedSocialSignupToken;
import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.ConsumedSignupTokenRepository;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
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
import java.time.Clock;
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
    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final ConsumedSignupTokenRepository consumedSignupTokenRepository;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final SignatureImageUploadIssuer signatureImageUploadIssuer;
    private final SocialSignupTokenIssuer socialSignupTokenIssuer;
    private final AccessTokenIssuer accessTokenIssuer;
    private final SocialSignupTokenVerifier socialSignupTokenVerifier;
    private final SignatureImageStorage signatureImageStorage;
    private final SignatureImagePolicy signatureImagePolicy;
    private final UserRepository userRepository;
    private final UserRefreshTokenService userRefreshTokenService;
    private final Clock clock;

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

    public SocialSignupSignatureUploadResult createAppleSignatureUpload(
            String signupToken
    ) {
        VerifiedSocialSignupToken verifiedToken =
                socialSignupTokenVerifier.verify(signupToken);
        validateAppleSignupToken(verifiedToken);
        validateNewSocialAccount(new VerifiedSocialIdentity(
                verifiedToken.provider(),
                verifiedToken.subject(),
                verifiedToken.email()));

        SignatureImageUpload upload = signatureImageUploadIssuer.issue(
                verifiedToken.uploadId());
        return new SocialSignupSignatureUploadResult(
                upload,
                new IssuedSocialSignupToken(signupToken));
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
        validateNotReplayed(verifiedToken);

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
        SocialAccount socialAccount = socialAccountRepository.save(SocialAccount.create(
                user,
                verifiedToken.provider(),
                subjectHmac));
        saveAppleAuthorization(verifiedToken, socialAccount);

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

    private void saveAppleAuthorization(
            VerifiedSocialSignupToken verifiedToken,
            SocialAccount socialAccount
    ) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            return;
        }
        PendingAppleAuthorization pendingAuthorization =
                getPendingAuthorizationForUpdate(verifiedToken.uploadId());
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                pendingAuthorization.getEncryptedRefreshToken()));
        pendingAuthorizationRepository.delete(pendingAuthorization);
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

    /**
     * signupToken은 발급 후 만료 전까지 몇 번이든 검증에 통과하는 stateless JWT라,
     * 이미 가입을 완료시킨 토큰이라도 재전송하면 다시 여기까지 온다. 탈퇴로 소셜 계정이
     * 삭제되면 subject가 다시 "미가입"으로 보여, 같은 토큰으로 새 계정과 Apple 인증
     * 정보(이미 폐기된 RT 포함)가 재구성될 수 있다. 이 토큰의 jti를 최초 가입 성공
     * 시점에 소진 처리해, 같은 토큰의 두 번째 가입 완료를 막는다.
     */
    private void validateNotReplayed(VerifiedSocialSignupToken verifiedToken) {
        boolean firstUse = consumedSignupTokenRepository.consumeIfAbsent(
                ConsumedSignupToken.create(
                        verifiedToken.tokenId(),
                        verifiedToken.expiresAt()));
        if (!firstUse) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "이미 사용된 회원가입 토큰입니다.");
        }
    }

    private void validateUnusedSignature(SignatureStorageKeys storageKeys) {
        if (userRepository.existsBySignatureOriginalStorageKey(
                storageKeys.originalStorageKey())) {
            throw new NotFoundException(
                    ErrorCode.BUSINESS_ERROR,
                    "업로드한 사인 이미지를 찾을 수 없습니다.");
        }
    }

    private void validateAppleSignupToken(
            VerifiedSocialSignupToken verifiedToken
    ) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "Apple 회원가입 토큰이 아닙니다.");
        }
        getPendingAuthorization(verifiedToken.uploadId());
    }

    private PendingAppleAuthorization getPendingAuthorization(UUID uploadId) {
        PendingAppleAuthorization authorization = pendingAuthorizationRepository
                .findByUploadId(uploadId)
                .orElseThrow(this::pendingAuthorizationNotFound);
        validatePendingAuthorizationNotExpired(authorization);
        return authorization;
    }

    private PendingAppleAuthorization getPendingAuthorizationForUpdate(
            UUID uploadId
    ) {
        PendingAppleAuthorization authorization = pendingAuthorizationRepository
                .findByUploadIdForUpdate(uploadId)
                .orElseThrow(this::pendingAuthorizationNotFound);
        validatePendingAuthorizationNotExpired(authorization);
        return authorization;
    }

    private void validatePendingAuthorizationNotExpired(
            PendingAppleAuthorization authorization
    ) {
        if (authorization.isExpired(clock.instant())) {
            throw pendingAuthorizationNotFound();
        }
    }

    private BusinessException pendingAuthorizationNotFound() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 회원가입 인증 정보가 만료되었거나 없습니다. 다시 로그인해 주세요.");
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
