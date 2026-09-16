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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppleSignupAuthorizationService {

    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final Clock clock;

    public void validate(VerifiedSocialSignupToken verifiedToken) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_ERROR,
                    "Apple 회원가입 토큰이 아닙니다.");
        }
        getPendingAuthorization(verifiedToken);
    }

    /**
     * 한 신원에 여러 임시 인증 정보가 남아 있어도 최신 하나만 정식 보관으로 옮긴다. 나머지는
     * 이미 교환된 Refresh Token이라 삭제하면 Apple에 폐기할 수 없는 grant가 남으므로,
     * 정리 스케줄러가 만료 후 폐기하도록 그대로 둔다.
     */
    public void saveIfApple(
            VerifiedSocialSignupToken verifiedToken,
            SocialAccount socialAccount
    ) {
        if (verifiedToken.provider() != SocialProvider.APPLE) {
            return;
        }
        PendingAppleAuthorization pendingAuthorization =
                getPendingAuthorizationForUpdate(verifiedToken);
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                pendingAuthorization.getEncryptedRefreshToken()));
        pendingAuthorizationRepository.delete(pendingAuthorization);
    }

    private PendingAppleAuthorization getPendingAuthorization(
            VerifiedSocialSignupToken verifiedToken
    ) {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmac(
                        subjectHmac(verifiedToken),
                        clock.instant())
                .orElseThrow(this::pendingAuthorizationNotFound);
    }

    private PendingAppleAuthorization getPendingAuthorizationForUpdate(
            VerifiedSocialSignupToken verifiedToken
    ) {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        subjectHmac(verifiedToken),
                        clock.instant())
                .orElseThrow(this::pendingAuthorizationNotFound);
    }

    private String subjectHmac(VerifiedSocialSignupToken verifiedToken) {
        return fingerprintEncoder.encode(
                verifiedToken.provider(),
                verifiedToken.subject());
    }

    private BusinessException pendingAuthorizationNotFound() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 회원가입 인증 정보가 만료되었거나 없습니다. 다시 로그인해 주세요.");
    }
}
