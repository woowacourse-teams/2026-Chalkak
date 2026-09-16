package com.chalkak.backend.auth.service;

import com.chalkak.backend.auth.domain.AppleAuthorization;
import com.chalkak.backend.auth.domain.PendingAppleAuthorization;
import com.chalkak.backend.auth.domain.SocialAccount;
import com.chalkak.backend.auth.domain.SocialProvider;
import com.chalkak.backend.auth.domain.VerifiedSocialIdentity;
import com.chalkak.backend.auth.domain.VerifiedSocialSignupToken;
import com.chalkak.backend.auth.repository.AppleAuthorizationRepository;
import com.chalkak.backend.auth.repository.PendingAppleAuthorizationRepository;
import com.chalkak.backend.exception.BusinessException;
import com.chalkak.backend.exception.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AppleSignupAuthorizationService {

    private final AppleAuthorizationRepository appleAuthorizationRepository;
    private final PendingAppleAuthorizationRepository pendingAuthorizationRepository;
    private final SocialIdentityFingerprintEncoder fingerprintEncoder;
    private final Clock clock;

    /**
     * Apple 신규 회원은 로그인 때 보관한 Refresh Token이 있어야 가입을 끝낼 수 있으므로,
     * 보관분이 없거나 만료됐으면 업로드 URL을 발급하지 않는다. 남아 있으면 회원가입 토큰이
     * 살아 있는 동안 보관분이 먼저 만료되지 않도록 만료를 맞춘다.
     */
    @Transactional
    public void extendIfApple(VerifiedSocialIdentity identity, Instant expiresAt) {
        if (identity.provider() != SocialProvider.APPLE) {
            return;
        }
        PendingAppleAuthorization pendingAuthorization = getPendingAuthorization(
                fingerprintEncoder.encode(
                        identity.provider(),
                        identity.subject()));
        pendingAuthorization.extendTo(expiresAt);
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
                getPendingAuthorizationForUpdate(fingerprintEncoder.encode(
                        verifiedToken.provider(),
                        verifiedToken.subject()));
        appleAuthorizationRepository.save(AppleAuthorization.create(
                socialAccount,
                pendingAuthorization.getEncryptedRefreshToken()));
        pendingAuthorizationRepository.delete(pendingAuthorization);
    }

    private PendingAppleAuthorization getPendingAuthorization(String subjectHmac) {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmac(subjectHmac, clock.instant())
                .orElseThrow(this::pendingAuthorizationNotFound);
    }

    private PendingAppleAuthorization getPendingAuthorizationForUpdate(
            String subjectHmac
    ) {
        return pendingAuthorizationRepository
                .findLatestUnexpiredBySubjectHmacForUpdate(
                        subjectHmac,
                        clock.instant())
                .orElseThrow(this::pendingAuthorizationNotFound);
    }

    private BusinessException pendingAuthorizationNotFound() {
        return new BusinessException(
                ErrorCode.BUSINESS_ERROR,
                "Apple 회원가입 인증 정보가 만료되었거나 없습니다. 다시 로그인해 주세요.");
    }
}
