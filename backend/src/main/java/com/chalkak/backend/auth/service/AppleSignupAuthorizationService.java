package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleSignupAuthorizationService {

    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final Clock clock;

    public void validate(VerifiedSocialSignupToken verifiedToken) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "Apple 회원가입 토큰이 아닙니다.");
        }
        getPendingAuthorization(verifiedToken.uploadId());
    }

    public void saveIfApple(
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

    private PendingAppleAuthorization getPendingAuthorization(UUID uploadId) {
        PendingAppleAuthorization authorization = pendingAuthorizationRepository
                .findByUploadId(uploadId)
                .orElseThrow(this::pendingAuthorizationNotFound);
        validateNotExpired(authorization);
        return authorization;
    }

    private PendingAppleAuthorization getPendingAuthorizationForUpdate(
            UUID uploadId
    ) {
        PendingAppleAuthorization authorization = pendingAuthorizationRepository
                .findByUploadIdForUpdate(uploadId)
                .orElseThrow(this::pendingAuthorizationNotFound);
        validateNotExpired(authorization);
        return authorization;
    }

    private void validateNotExpired(PendingAppleAuthorization authorization) {
        if (authorization.isExpired(clock.instant())) {
            throw pendingAuthorizationNotFound();
        }
    }

    private BusinessException pendingAuthorizationNotFound() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 회원가입 인증 정보가 만료되었거나 없습니다. 다시 로그인해 주세요.");
    }
}
